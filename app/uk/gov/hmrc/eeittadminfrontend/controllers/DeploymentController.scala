/*
 * Copyright 2021 HM Revenue & Customs
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
import org.slf4j.{ Logger, LoggerFactory }
import play.api.i18n.I18nSupport
import play.api.libs.json.Json
import play.api.mvc.{ AnyContent, MessagesControllerComponents, Request, Result }
import play.twirl.api.{ Html, HtmlFormat }
import scala.concurrent.{ ExecutionContext, Future }
import uk.gov.hmrc.eeittadminfrontend.auth.AuthConnector
import uk.gov.hmrc.eeittadminfrontend.config.{ AppConfig, AuthAction }
import uk.gov.hmrc.eeittadminfrontend.deployment.{ BlobSha, DeploymentDiff, DeploymentRecord, Filename, GithubContent, MongoContent, Reconciliation, ReconciliationLookup }
import uk.gov.hmrc.eeittadminfrontend.diff.DiffMaker
import uk.gov.hmrc.eeittadminfrontend.models.github.{ Authorization, LastCommitCheck, PrettyPrintJson }
import uk.gov.hmrc.eeittadminfrontend.models.FormTemplateId
import uk.gov.hmrc.eeittadminfrontend.services.{ CacheStatus, CachingService, DeploymentService, GformService, GithubService }
import uk.gov.hmrc.eeittadminfrontend.validators.FormTemplateValidator
import uk.gov.hmrc.govukfrontend.views.html.components._
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

class DeploymentController(
  val authConnector: AuthConnector,
  authAction: AuthAction,
  gformService: GformService,
  githubService: GithubService,
  deploymentService: DeploymentService,
  cachingService: CachingService,
  formTemplateValidator: FormTemplateValidator,
  authorization: Authorization,
  messagesControllerComponents: MessagesControllerComponents
)(implicit ec: ExecutionContext, appConfig: AppConfig)
    extends FrontendController(messagesControllerComponents) with I18nSupport {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  private val ioToFuture: IO ~> Future = new (IO ~> Future) {
    def apply[A](fa: IO[A]): Future[A] = fa.unsafeToFuture()
  }

  def download(formTemplateId: FormTemplateId) = authAction.async { request =>
    EitherT(gformService.getFormTemplate(formTemplateId)).fold(
      error => Ok(s"Problem when fetching form template: $formTemplateId. Reason: $error"),
      mongoContent => Ok(PrettyPrintJson.asString(mongoContent.json))
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

  private def compareDeploymentDiff(sha1: BlobSha, sha2: BlobSha): EitherT[IO, String, Html] = for {
    blob1 <- githubService.getBlob(sha1)
    blob2 <- githubService.getBlob(sha2)
  } yield {
    val diff = DiffMaker.getDiff(sha1.value, sha2.value, blob1, blob2)
    uk.gov.hmrc.eeittadminfrontend.views.html.deployment_diff(Html(diff))
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
        compareDeploymentDiff(sha1, sha2)
      }

    diff
      .map { diffHtmlData =>
        val diffData = deploymentDiffs.map(_.toTableRow(authorization, sha1, sha2))
        val table: Table = historyTable(diffData)
        Ok(uk.gov.hmrc.eeittadminfrontend.views.html.deployment_history(formTemplateId, table, diffHtmlData))
      }
      .mapK(ioToFuture)
      .fold(error => BadRequest(error), identity)
  }

  def deployFilename(filename: Filename) = authAction.async { implicit request =>
    def logDeploymentStatus(message: String): Unit =
      logger.info(s"${request.userData} deployment of filename ${filename.value} " + message)

    withGithubContentFromCache(filename) { githubContent =>
      logDeploymentStatus("started")

      val deploymentRecord = DeploymentRecord(
        username = request.userData.username,
        createdAt = Instant.now(),
        filename = filename,
        formTemplateId = githubContent.formTemplateId,
        blobSha = githubContent.blobSha,
        commitSha = githubContent.commitSha
      )

      gformService
        .saveTemplate(githubContent.formTemplateId, githubContent.json)
        .flatMap { unit =>
          EitherT.rightT[Future, String](deploymentService.save(deploymentRecord))
        }
        .bimap(
          error => {
            logDeploymentStatus(s"failed with: $error")
            error
          },
          writeResult => {
            val formTemplateId = githubContent.formTemplateId
            logDeploymentStatus(formTemplateId.value + " successfully deployed")
            Ok(uk.gov.hmrc.eeittadminfrontend.views.html.deployment_success(formTemplateId, filename))
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
            EitherT(gformService.getFormTemplate(formTemplateId)),
            githubService.getCommit(githubContent.commitSha).mapK(ioToFuture),
            EitherT.right[String](deploymentService.find(formTemplateId)),
            EitherT.liftF[Future, String, Either[String, Unit]](formTemplateValidator.validate(githubContent.json))
          ).parMapN { case (mongoTemplate, commit, deploymentRecords, validationResult) =>
            val validationWarning: Option[String] = validationResult.swap.toOption
            val diff = DiffMaker.getDiff(filename, mongoTemplate, githubContent)
            val inSync = DiffMaker.inSync(mongoTemplate, githubContent)
            val diffHtml = uk.gov.hmrc.eeittadminfrontend.views.html.deployment_diff(Html(diff))
            Ok(
              uk.gov.hmrc.eeittadminfrontend.views.html
                .deployment_existing(
                  formTemplateId,
                  diffHtml,
                  commit,
                  githubContent,
                  filename,
                  inSync,
                  deploymentRecords.headOption,
                  lastCommitCheck,
                  authorization,
                  validationWarning
                )
            )
          }
        }
      }
    }

  def delete(formTemplateId: FormTemplateId) =
    authAction.async { implicit request =>
      logger.info(s"${request.userData} is deleting ${formTemplateId.value}")
      gformService.deleteTemplate(formTemplateId).map { deleteResults =>
        logger.info(s"${request.userData} deleted ${formTemplateId.value}: $deleteResults")
        Ok(Json.toJson(deleteResults))
      }
    }

  def deploymentDeleted(formTemplateId: FormTemplateId) = authAction.async { implicit request =>
    Ok(uk.gov.hmrc.eeittadminfrontend.views.html.deployment_deleted(formTemplateId)).pure[Future]
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
            EitherT.liftF[Future, String, Either[String, Unit]](formTemplateValidator.validate(githubContent.json))
          ).parMapN { case (commit, validationResult) =>
            val validationWarning: Option[String] = validationResult.swap.toOption
            Ok(
              uk.gov.hmrc.eeittadminfrontend.views.html
                .deployment_new(
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
    Ok(uk.gov.hmrc.eeittadminfrontend.views.html.deployment_home(authorization, cachingService.cacheStatus))
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
      withLastCommit { lastCommitCheck =>
        EitherT(gformService.getAllGformsTemplates)
          .flatMap { mongoTemplateIds =>
            val message = s"${request.userData} Loading ${mongoTemplateIds.size} templates from MongoDB"
            logger.info(message + " - Start")
            val mongoTemplatesM: EitherT[Future, String, List[MongoContent]] =
              mongoTemplateIds.parTraverse { formTemplateId =>
                EitherT(gformService.getFormTemplate(formTemplateId))
              }

            mongoTemplatesM.map { mongoTemplates =>
              logger.info(message + " - Done")
              val reconciliationLookup = toReconciliationLookup(mongoTemplates, cachingService.githubContents)

              Ok(
                uk.gov.hmrc.eeittadminfrontend.views.html
                  .deployments(
                    reconciliationLookup,
                    existingTemplatesTable(reconciliationLookup.existingTemplates),
                    newTemplatesTable(reconciliationLookup.newTemplates),
                    lastCommitCheck,
                    authorization
                  )
              )
            }
          }
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
          Reconciliation
            .Deleted(formTemplateId, routes.DeploymentController.deploymentDeleted(formTemplateId))
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
