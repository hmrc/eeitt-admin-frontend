/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.eeittadminfrontend.controllers

import cats._
import cats.data.NonEmptyList
import cats.syntax.all._
import cats.data.EitherT
import cats.effect.IO

import java.time.Instant
import javax.inject.Inject
import org.slf4j.{ Logger, LoggerFactory }
import play.api.i18n.I18nSupport
import play.api.libs.json.Json
import play.api.mvc.{ AnyContent, MessagesControllerComponents, Request, Result }
import play.twirl.api.{ Html, HtmlFormat }

import scala.concurrent.{ ExecutionContext, Future }
import uk.gov.hmrc.eeittadminfrontend.deployment.{ BlobSha, ContentValue, DeploymentDiff, DeploymentRecord, Filename, GithubContent, MongoContent, Reconciliation, ReconciliationLookup }
import uk.gov.hmrc.eeittadminfrontend.diff.{ DiffConfig, DiffMaker }
import uk.gov.hmrc.eeittadminfrontend.models.{ FormTemplateId, Username }
import uk.gov.hmrc.eeittadminfrontend.models.github.{ Authorization, LastCommitCheck, PrettyPrintJson }
import uk.gov.hmrc.eeittadminfrontend.services.{ CacheStatus, CachingService, DeploymentService, GformService, GithubService }
import uk.gov.hmrc.eeittadminfrontend.validators.FormTemplateValidator
import uk.gov.hmrc.govukfrontend.views.html.components._
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents

class DeploymentController @Inject() (
  authorization: Authorization,
  frontendAuthComponents: FrontendAuthComponents,
  gformService: GformService,
  githubService: GithubService,
  deploymentService: DeploymentService,
  cachingService: CachingService,
  formTemplateValidator: FormTemplateValidator,
  messagesControllerComponents: MessagesControllerComponents,
  deployment_new: uk.gov.hmrc.eeittadminfrontend.views.html.deployment_new,
  deployment_existing: uk.gov.hmrc.eeittadminfrontend.views.html.deployment_existing,
  deploymentsView: uk.gov.hmrc.eeittadminfrontend.views.html.deployments,
  deployment_home: uk.gov.hmrc.eeittadminfrontend.views.html.deployment_home,
  deployment_deleted: uk.gov.hmrc.eeittadminfrontend.views.html.deployment_deleted,
  deployment_history: uk.gov.hmrc.eeittadminfrontend.views.html.deployment_history,
  deployment_success: uk.gov.hmrc.eeittadminfrontend.views.html.deployment_success,
  handlebars_template: uk.gov.hmrc.eeittadminfrontend.views.html.handlebars_template,
  diffConfig: DiffConfig
)(implicit ec: ExecutionContext)
    extends GformAdminFrontendController(frontendAuthComponents, messagesControllerComponents) with I18nSupport {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  private val ioToFuture: IO ~> Future = new (IO ~> Future) {
    def apply[A](fa: IO[A]): Future[A] = fa.unsafeToFuture()
  }

  def download(formTemplateId: FormTemplateId) = authAction.async { request =>
    EitherT(gformService.getFormTemplate(formTemplateId)).fold(
      error => Ok(s"Problem when fetching form template: $formTemplateId. Reason: $error"),
      mongoContent => Ok(PrettyPrintJson.asString(mongoContent.content.jsonContent))
    )
  }

  def downloadHandlebarsTemplate(formTemplateId: FormTemplateId) = authAction.async { implicit request =>
    EitherT(gformService.getHandlebarsTemplate(formTemplateId)).fold(
      error => Ok(s"Problem when fetching handlebars template: ${formTemplateId.value}. Reason: $error"),
      mongoContent => Ok(handlebars_template(formTemplateId, Html(mongoContent.content.textContent)))
    )
  }

  private def mkDeploymentDiff(deploymentRecords: List[DeploymentRecord]): NonEmptyList[DeploymentDiff] = {
    val deploymentDiffsDouble: List[DeploymentDiff.Double] = deploymentRecords
      .sliding(2)
      .toList
      .collect { case sha1 :: sha2 :: Nil => DeploymentDiff.Double(sha2, sha1) }

    val lastDeploymentDiff =
      deploymentRecords.lastOption.fold[DeploymentDiff](DeploymentDiff.None)(lastDeploymentDiff =>
        DeploymentDiff.Single(lastDeploymentDiff)
      )
    (NonEmptyList.one(lastDeploymentDiff) ++ deploymentDiffsDouble.reverse).reverse
  }

  private def compareDeploymentDiff(
    formTemplateId: FormTemplateId,
    sha1: BlobSha,
    sha2: BlobSha
  ): EitherT[IO, String, Html] = {
    val fileName = Filename(formTemplateId.value)
    for {
      blob1 <- githubService.getBlob(fileName, sha1)
      blob2 <- githubService.getBlob(fileName, sha2)
    } yield {
      val diff = DiffMaker.getDiff(
        sha1.value,
        sha2.value,
        blob1,
        blob2,
        diffConfig.timeout
      )
      uk.gov.hmrc.eeittadminfrontend.views.html.deployment_diff(Html(diff))
    }
  }

  def history1(formTemplateId: FormTemplateId) = authAction.async { implicit request =>
    deploymentService.find(formTemplateId).flatMap { deploymentRecords =>
      val deploymentDiffs = mkDeploymentDiff(deploymentRecords)
      val (sha1, sha2) = deploymentDiffs.head.toSha
      hist(formTemplateId, deploymentDiffs, sha1, sha2)
    }
  }

  def history(formTemplateId: FormTemplateId, sha1: BlobSha, sha2: BlobSha) = authAction.async { implicit request =>
    deploymentService.find(formTemplateId).flatMap { deploymentRecords =>
      val deploymentDiffs = mkDeploymentDiff(deploymentRecords)
      hist(formTemplateId, deploymentDiffs, sha1, sha2)
    }
  }

  def hist(formTemplateId: FormTemplateId, deploymentDiffs: NonEmptyList[DeploymentDiff], sha1: BlobSha, sha2: BlobSha)(
    implicit request: Request[AnyContent]
  ): Future[Result] = {

    val noDiff = EitherT.rightT[IO, String](HtmlFormat.empty)

    val diff = deploymentDiffs.head
      .fold(_ => noDiff)(_ => noDiff) { _ =>
        compareDeploymentDiff(formTemplateId, sha1, sha2)
      }

    diff
      .map { diffHtmlData =>
        val diffData = deploymentDiffs.map(_.toTableRow(authorization, sha1, sha2))
        val table: Table = historyTable(diffData)
        Ok(deployment_history(formTemplateId, table, diffHtmlData))
      }
      .mapK(ioToFuture)
      .fold(error => BadRequest(error), identity)
  }

  def deployFilename(filename: Filename) = authAction.async { implicit request =>
    val username = request.retrieval
    def logDeploymentStatus(message: String): Unit =
      logger.info(s"$username deployment of filename ${filename.value} " + message)

    withGithubContentFromCache(filename) { githubContent =>
      logDeploymentStatus("started")

      val deploymentRecord = DeploymentRecord(
        username = Username.fromRetrieval(username),
        createdAt = Instant.now(),
        filename = filename,
        formTemplateId = githubContent.formTemplateId,
        blobSha = githubContent.blobSha,
        commitSha = githubContent.commitSha
      )

      val saveResult = githubContent.content match {
        case ContentValue.JsonContent(json) =>
          gformService.saveTemplate(githubContent.formTemplateId, json)
        case ContentValue.TextContent(payload) =>
          gformService.saveHandlebarsTemplate(githubContent.formTemplateId, payload)
      }

      val saveDeployment =
        saveResult.flatMap(_ => EitherT.rightT[Future, String](deploymentService.save(deploymentRecord)))

      saveDeployment.bimap(
        error => {
          logDeploymentStatus(s"failed with: $error")
          error
        },
        _ => {
          val formTemplateId = githubContent.formTemplateId
          logDeploymentStatus(formTemplateId.value + " successfully deployed")
          Ok(deployment_success(formTemplateId, filename))
        }
      )
    }.fold(error => BadRequest(error), identity)
  }

  def deploymentExisting(
    formTemplateId: FormTemplateId,
    filename: Filename
  ) =
    authAction.async { implicit request =>
      withLastCommit { lastCommitCheck =>
        withGithubContentFromCache(filename) { githubContent =>
          (
            if (filename.isJson) EitherT(gformService.getFormTemplate(formTemplateId))
            else EitherT(gformService.getRawHandlebarsTemplate(formTemplateId)),
            githubService.getCommit(githubContent.commitSha).mapK(ioToFuture),
            EitherT.right[String](deploymentService.find(formTemplateId)),
            EitherT.liftF[Future, String, Either[String, Unit]](
              formTemplateValidator.validate(githubContent.content.jsonContent)
            ),
            EitherT(
              gformService
                .retrieveContentsForHandlebars(formTemplateId, cachingService.githubContents, filename.isJson)
            )
          ).parMapN {
            case (
                  mongoTemplate,
                  commit,
                  deploymentRecords,
                  validationResult,
                  (mongoTemplateHandelbars, githubContentHandelbars)
                ) =>
              val validationWarning: Option[String] = validationResult.swap.toOption
              val diff = DiffMaker.getDiff(filename, mongoTemplate, githubContent, diffConfig.timeout)
              val inSync = DiffMaker.inSync(mongoTemplate, githubContent)
              val diffHtml = uk.gov.hmrc.eeittadminfrontend.views.html.deployment_diff(Html(diff))
              val downloadLink = if (filename.isJson) {
                uk.gov.hmrc.eeittadminfrontend.views.html.deployment_link_download_gform(formTemplateId)
              } else {
                uk.gov.hmrc.eeittadminfrontend.views.html.deployment_link_download_handlebars(formTemplateId)
              }
              val reconciliationLookup: ReconciliationLookup =
                toReconciliationLookup(mongoTemplateHandelbars, githubContentHandelbars)

              Ok(
                deployment_existing(
                  formTemplateId,
                  diffHtml,
                  commit,
                  githubContent,
                  filename,
                  inSync,
                  deploymentRecords.headOption,
                  lastCommitCheck,
                  authorization,
                  validationWarning,
                  downloadLink,
                  existingTemplatesTable(reconciliationLookup.existingTemplates),
                  reconciliationLookup
                )
              )
          }
        }
      }
    }

  def delete(formTemplateId: FormTemplateId) =
    authAction.async { implicit request =>
      val username = request.retrieval.value
      logger.info(s"$username is deleting ${formTemplateId.value}")
      gformService.deleteTemplate(formTemplateId).map { deleteResults =>
        logger.info(s"$username deleted ${formTemplateId.value}: $deleteResults")
        Ok(Json.toJson(deleteResults))
      }
    }

  def deleteHandlebarsTemplate(formTemplateId: FormTemplateId) =
    authAction.async { implicit request =>
      val username = request.retrieval.value
      logger.info(s"$username is deleting ${formTemplateId.value}")
      gformService.deleteHandlebarsTemplate(formTemplateId).map { deleteResult =>
        logger.info(s"$username deleted ${formTemplateId.value}: $deleteResult")
        Ok(Json.toJson(deleteResult))
      }
    }

  def deploymentDeleted(formTemplateId: FormTemplateId, isJson: Boolean = false) = authAction.async {
    implicit request =>
      val downloadLink = if (isJson) {
        uk.gov.hmrc.eeittadminfrontend.views.html.deployment_link_download_gform(formTemplateId)
      } else {
        uk.gov.hmrc.eeittadminfrontend.views.html.deployment_link_download_handlebars(formTemplateId)
      }

      val deleteAction = if (isJson) {
        uk.gov.hmrc.eeittadminfrontend.controllers.routes.DeploymentController.delete(formTemplateId)
      } else {
        uk.gov.hmrc.eeittadminfrontend.controllers.routes.DeploymentController.deleteHandlebarsTemplate(formTemplateId)
      }
      Ok(deployment_deleted(formTemplateId, downloadLink, deleteAction)).pure[Future]
  }

  def deploymentNew(
    formTemplateId: FormTemplateId,
    filename: Filename,
    sha: BlobSha
  ) =
    authAction.async { implicit request =>
      withLastCommit { lastCommitCheck =>
        withGithubContentFromCache(filename) { githubContent =>
          (
            githubService.getCommit(githubContent.commitSha).mapK(ioToFuture),
            EitherT.liftF[Future, String, Either[String, Unit]](
              formTemplateValidator.validate(githubContent.content.jsonContent)
            )
          ).parMapN { case (commit, validationResult) =>
            val validationWarning: Option[String] = validationResult.swap.toOption
            Ok(
              deployment_new(
                formTemplateId,
                commit,
                githubContent,
                filename,
                lastCommitCheck,
                authorization,
                validationWarning
              )
            )
          }
        }
      }
    }

  private def withGithubContentFromCache(
    filename: Filename
  )(f: GithubContent => EitherT[Future, String, Result]): EitherT[Future, String, Result] =
    cachingService
      .githubContent(filename)
      .fold(EitherT.leftT[Future, Result](s"No ${filename.value} found in the cache")) { githubContent =>
        f(githubContent)
      }

  def refreshCache(redirectUrl: String) = authAction.async { request =>
    cachingService.refreshCache
    Redirect(redirectUrl).pure[Future]
  }

  def deploymentHome = authAction.async { implicit request =>
    Ok(deployment_home(authorization, cachingService.cacheStatus))
      .pure[Future]
  }

  private def withLastCommit(f: LastCommitCheck => EitherT[Future, String, Result]): Future[Result] = {
    val resultE: EitherT[Future, String, Result] = cachingService.cacheStatus match {
      case r @ CacheStatus.Refreshed(cacheCommit) =>
        githubService.lastCommit
          .mapK(ioToFuture)
          .flatMap { lastCommit =>
            f(LastCommitCheck(r, lastCommit))
          }

      case _ => EitherT.leftT("Cache of Github templates needs refreshing")
    }
    resultE.fold(error => BadRequest(error), identity)
  }

  def deployments =
    authAction.async { implicit request =>
      val username = request.retrieval.value
      withLastCommit { lastCommitCheck =>
        for {
          mongoTemplateIds <- EitherT(gformService.getAllGformsTemplates)
          mongoHandlebarsIds <- {
            logger.info(s"$username Loading ${mongoTemplateIds.size} templates from MongoDB")
            EitherT(gformService.getAllHandlebarsTemplates)
          }
          mongoContentsForTemplate <- {
            logger.info(s"$username Loading ${mongoHandlebarsIds.size} handlebars templates from MongoDB")
            mongoTemplateIds.parTraverse(formTemplateId => EitherT(gformService.getFormTemplate(formTemplateId)))
          }
          mongoContentsForHandlebars <-
            mongoHandlebarsIds.parTraverse(id => EitherT(gformService.getRawHandlebarsTemplate(id)))
          mongoTemplates = mongoContentsForTemplate ++ mongoContentsForHandlebars
          reconciliationLookup = toReconciliationLookup(mongoTemplates, cachingService.githubContents)
        } yield Ok(
          deploymentsView(
            reconciliationLookup,
            existingTemplatesTable(reconciliationLookup.existingTemplates),
            newTemplatesTable(reconciliationLookup.newTemplates),
            lastCommitCheck,
            authorization
          )
        )
      }
    }

  private def toReconciliationLookup(
    mongoTemplates: List[MongoContent],
    githubTemplates: List[(Filename, GithubContent)]
  ): ReconciliationLookup = {

    val mongoLookup: Map[FormTemplateId, MongoContent] =
      mongoTemplates.map(a => a.formTemplateId -> a).toMap

    val reconciliations: List[Reconciliation] = githubTemplates.map { case (filename, githubContent) =>
      reconciliation(
        mongoLookup,
        githubContent,
        filename
      )
    }

    val n: List[Reconciliation.New] = reconciliations.collect { case r: Reconciliation.New => r }
    val e: List[Reconciliation.Existing] = reconciliations.collect { case r: Reconciliation.Existing => r }

    val allGithubTemplatesId: List[FormTemplateId] = reconciliations.map(_.formTemplateId)
    val deletedTemplates: List[Reconciliation.Deleted] =
      mongoLookup.keys.toList
        .filter(ftId => !allGithubTemplatesId.contains_(ftId))
        .sortBy(_.value)
        .map(formTemplateId =>
          mongoLookup.get(formTemplateId).map(_.content) match {
            case Some(ContentValue.JsonContent(_)) =>
              Reconciliation
                .Deleted(formTemplateId, routes.DeploymentController.deploymentDeleted(formTemplateId, true))
            case Some(ContentValue.TextContent(_)) =>
              Reconciliation
                .Deleted(formTemplateId, routes.DeploymentController.deploymentDeleted(formTemplateId, false))
            case None => throw new RuntimeException(s"Content not found for form template: $formTemplateId")
          }
        )
    ReconciliationLookup(n.groupBy(_.formTemplateId), e.groupBy(_.formTemplateId), deletedTemplates)
  }

  private def reconciliation(
    mongoLookup: Map[FormTemplateId, MongoContent],
    githubContent: GithubContent,
    filename: Filename
  ): Reconciliation = {

    val formTemplateId = githubContent.formTemplateId
    mongoLookup.get(formTemplateId) match {
      case None =>
        Reconciliation.New(
          formTemplateId,
          filename,
          routes.DeploymentController.deploymentNew(
            formTemplateId,
            filename,
            githubContent.blobSha
          )
        )
      case Some(mongoContent) =>
        val inSync = DiffMaker.inSync(mongoContent, githubContent)
        Reconciliation.Existing(
          formTemplateId,
          filename,
          routes.DeploymentController
            .deploymentExisting(formTemplateId, filename),
          inSync
        )
    }
  }

  private def existingTemplatesTable(existing: Map[FormTemplateId, List[Reconciliation.Existing]]): Table =
    Table(
      rows =
        for {
          (_, reconciliations) <- existing.toList.sortBy(_._1.value)
          reconciliation       <- reconciliations
        } yield Seq(
          TableRow(
            content = HtmlContent(uk.gov.hmrc.eeittadminfrontend.views.html.deployment_link(reconciliation))
          ),
          TableRow(
            content = Text(reconciliation.filename.value)
          ),
          TableRow(
            content = Text(if (reconciliation.inSync) "" else "Out of sync")
          )
        ),
      head = Some(
        Seq(
          HeadCell(
            content = Text("Template Id")
          ),
          HeadCell(
            content = Text("Filename")
          ),
          HeadCell(
            content = Text("Status")
          )
        )
      ),
      firstCellIsHeader = true
    )

  private def newTemplatesTable(reconciliationNew: Map[FormTemplateId, List[Reconciliation.New]]): Table =
    Table(
      rows =
        for {
          (_, reconciliations) <- reconciliationNew.toList.sortBy(_._1.value)
          reconciliation       <- reconciliations
        } yield Seq(
          TableRow(
            content = HtmlContent(uk.gov.hmrc.eeittadminfrontend.views.html.deployment_link_new(reconciliation))
          ),
          TableRow(
            content = Text(reconciliation.filename.value)
          )
        ),
      head = Some(
        Seq(
          HeadCell(
            content = Text("Template Id")
          ),
          HeadCell(
            content = Text("Filename")
          )
        )
      ),
      firstCellIsHeader = true
    )

  private def historyTable(
    rows: NonEmptyList[Seq[TableRow]]
  ): Table = {
    val head = Some(
      Seq(
        HeadCell(
          content = Text("Username")
        ),
        HeadCell(
          content = Text("Deployed at")
        ),
        HeadCell(
          content = Text("Commit sha")
        ),
        HeadCell(
          content = Text("Filename")
        ),
        HeadCell(
          content = Text("Blob sha")
        )
      )
    )
    Table(
      rows = rows.toList,
      head = head,
      firstCellIsHeader = false
    )
  }
}
