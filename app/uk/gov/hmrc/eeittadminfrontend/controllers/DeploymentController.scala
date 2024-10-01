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
import io.circe.{ JsonObject, Json => CJson }
import io.circe.syntax._

import java.time.Instant
import javax.inject.Inject
import org.slf4j.{ Logger, LoggerFactory }
import play.api.i18n.I18nSupport
import play.api.libs.json.Json
import play.api.mvc.{ AnyContent, Call, MessagesControllerComponents, Request, Result }
import play.twirl.api.{ Html, HtmlFormat }
import uk.gov.hmrc.eeittadminfrontend.deployment.GithubPath.asPath

import scala.concurrent.{ ExecutionContext, Future }
import uk.gov.hmrc.eeittadminfrontend.deployment.{ BlobSha, ContentValue, DeploymentDiff, DeploymentRecord, Filename, FullPath, GithubContent, GithubPath, MongoContent, Reconciliation, ReconciliationLookup }
import uk.gov.hmrc.eeittadminfrontend.diff.{ DiffConfig, DiffMaker }
import uk.gov.hmrc.eeittadminfrontend.models.{ CircePlayHelpers, FormTemplateId, Username }
import uk.gov.hmrc.eeittadminfrontend.models.github.{ Authorization, LastCommitCheck, PrettyPrintJson }
import uk.gov.hmrc.eeittadminfrontend.services.{ CacheStatus, CachingService, DeploymentService, GformService, GithubService }
import uk.gov.hmrc.eeittadminfrontend.validators.FormTemplateValidator
import uk.gov.hmrc.govukfrontend.views.html.components._
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl.idFunctor
import uk.gov.hmrc.play.bootstrap.binders.{ OnlyRelative, RedirectUrl }

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
  deployment_confirm_no_version_change: uk.gov.hmrc.eeittadminfrontend.views.html.deployment_confirm_no_version_change,
  deployment_confirm_allow_old_version_journey: uk.gov.hmrc.eeittadminfrontend.views.html.deployment_confirm_allow_old_version_journey,
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

  def download(formTemplateId: FormTemplateId) = authorizedRead.async { request =>
    EitherT(gformService.getFormTemplate(formTemplateId)).fold(
      error => Ok(s"Problem when fetching form template: $formTemplateId. Reason: $error"),
      mongoContent => Ok(PrettyPrintJson.asString(mongoContent.content.jsonContent))
    )
  }

  def downloadHandlebarsTemplate(formTemplateId: FormTemplateId) = authorizedRead.async { implicit request =>
    EitherT(gformService.getHandlebarsTemplate(formTemplateId)).fold(
      error => Ok(s"Problem when fetching handlebars template: ${formTemplateId.value}. Reason: $error"),
      mongoContent => Ok(handlebars_template(formTemplateId, Html(mongoContent.content.textContent)))
    )
  }

  def downloadHandlebarsSchema(formTemplateId: FormTemplateId) = authorizedRead.async { request =>
    EitherT(gformService.getHandlebarsSchema(formTemplateId)).fold(
      error => Ok(s"Problem when fetching handlebars schema: ${formTemplateId.value}. Reason: $error"),
      mongoContent => Ok(PrettyPrintJson.asString(mongoContent.content.jsonContent))
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

  def history1(formTemplateId: FormTemplateId) = authorizedRead.async { implicit request =>
    deploymentService.find(formTemplateId).flatMap { deploymentRecords =>
      val deploymentDiffs = mkDeploymentDiff(deploymentRecords)
      val (sha1, sha2) = deploymentDiffs.head.toSha
      hist(formTemplateId, deploymentDiffs, sha1, sha2)
    }
  }

  def history(formTemplateId: FormTemplateId, sha1: BlobSha, sha2: BlobSha) = authorizedRead.async { implicit request =>
    deploymentService.find(formTemplateId).flatMap { deploymentRecords =>
      val deploymentDiffs = mkDeploymentDiff(deploymentRecords)
      deploymentDiffs.map(_.toTableRow(authorization, sha1, sha2))
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

  def cutNewVersion(formTemplateId: FormTemplateId, filename: Filename) =
    authorizedWrite.async { implicit request =>
      withLastCommit { lastCommitCheck =>
        withGithubContentFromCache(filename) { githubContent =>
          val githubJson: CJson = githubContent.content.jsonContent
          for {
            mongoContent <- EitherT(gformService.getFormTemplate(formTemplateId))
            mongoJson = mongoContent.content.jsonContent
            mongoVersion <- EitherT.fromEither[Future](getTemplateVersion(mongoJson))
            versionedTemplateId = formTemplateId.value + "-v" + mongoVersion
            mongoIdUpdated <- EitherT.fromEither[Future](
                                mongoJson.hcursor
                                  .downField("_id")
                                  .withFocus(_.mapString(_ => versionedTemplateId))
                                  .up
                                  .focus
                                  .toRight(s"Cannot find '_id' field in mongo json $mongoJson")
                              )
            updatedMongoJson = CJson.obj("accessibilityUrl" := formTemplateId.value).deepMerge(mongoIdUpdated)
            updatedGithubJson <- EitherT.fromEither[Future] {
                                   githubJson.asObject
                                     .map { jsonObject =>
                                       val updatedFields = jsonObject.toList.flatMap {
                                         case id @ ("_id", value) =>
                                           List(
                                             id,
                                             "legacyFormIds" -> CJson.arr(versionedTemplateId.asJson)
                                           )
                                         case ("legacyFormIds", _) => Nil // Remove existing legacyFormIds
                                         case keep                 => List(keep)
                                       }
                                       CJson.fromJsonObject(JsonObject.fromIterable(updatedFields))
                                     }
                                     .toRight(s"Github json error. Expected json object, but got: $githubJson")
                                 }
            updatedGithubContent =
              githubContent.copy(content = ContentValue.JsonContent(updatedGithubJson))
            _ <- for {
                   handlebarsIds <- EitherT.fromEither[Future](getHandlebarsPayloadIds(mongoJson))
                   _ <- handlebarsIds.traverse { handlebarId =>
                          val handlebarTemplateId = FormTemplateId(s"${formTemplateId.value}-$handlebarId")
                          val versionedHandlebarTemplateId =
                            FormTemplateId(s"${formTemplateId.value}-v$mongoVersion-$handlebarId")
                          for {
                            rawHandlebarsTemplate <- EitherT(gformService.getRawHandlebarsTemplate(handlebarTemplateId))
                            _ <- if (rawHandlebarsTemplate.content.textContent.nonEmpty)
                                   gformService.saveHandlebarsTemplate(
                                     versionedHandlebarTemplateId,
                                     rawHandlebarsTemplate.content.textContent
                                   )
                                 else EitherT.rightT[Future, String](())
                          } yield ()
                        }
                 } yield ()
            _ <- for {
                   handlebarsSchemaIds <- EitherT.fromEither[Future](getHandlebarsSchemaIds(mongoJson))
                   _ <- handlebarsSchemaIds.traverse { handlebarSchemaId =>
                          val versionedHandlebarSchemaId = FormTemplateId(s"${formTemplateId.value}-v$mongoVersion")
                          for {
                            handlebarsSchema <- EitherT(gformService.getHandlebarsSchema(formTemplateId))
                            _ <- if (!handlebarsSchema.content.jsonContent.isNull)
                                   gformService.saveHandlebarsSchema(
                                     versionedHandlebarSchemaId,
                                     CircePlayHelpers.circeToPlayUnsafe(handlebarsSchema.content.jsonContent)
                                   )
                                 else EitherT.rightT[Future, String](())
                          } yield ()
                        }
                 } yield ()
            _      <- gformService.saveTemplate(FormTemplateId(versionedTemplateId), updatedMongoJson)
            result <- deployGithubContent(Username.fromRetrieval(request.retrieval), filename, updatedGithubContent)
          } yield result
        }
      }
    }

  private def deployGithubContent(username: Username, filename: Filename, githubContent: GithubContent)(implicit
    request: Request[_]
  ): EitherT[Future, String, Result] = {
    logDeploymentStatus(username, filename, "started")

    val deploymentRecord = DeploymentRecord(
      username = username,
      createdAt = Instant.now(),
      filename = filename,
      formTemplateId = githubContent.formTemplateId,
      blobSha = githubContent.blobSha,
      commitSha = githubContent.commitSha
    )

    val saveResult = githubContent.path match {
      case GithubPath.RootPath =>
        gformService.saveTemplate(githubContent.formTemplateId, githubContent.content.jsonContent)
      case GithubPath.HandlebarsPath =>
        gformService.saveHandlebarsTemplate(githubContent.formTemplateId, githubContent.content.textContent)
      case GithubPath.HandlebarsSchemaPath =>
        gformService.saveHandlebarsSchema(
          githubContent.formTemplateId,
          CircePlayHelpers.circeToPlayUnsafe(githubContent.content.jsonContent)
        )
    }

    val saveDeployment =
      saveResult.flatMap(_ => EitherT.rightT[Future, String](deploymentService.save(deploymentRecord)))

    saveDeployment.bimap(
      error => {
        logDeploymentStatus(username, filename, s"failed with: $error")
        error
      },
      _ => {
        val formTemplateId = githubContent.formTemplateId
        logDeploymentStatus(username, filename, formTemplateId.value + " successfully deployed")
        Ok(deployment_success(formTemplateId, filename))
      }
    )
  }
  private def logDeploymentStatus(username: Username, filename: Filename, message: String): Unit =
    logger.info(s"$username deployment of filename ${filename.value} " + message)

  def deployFilenameGet(filename: Filename) = deployFilename(filename)

  def deployFilename(filename: Filename) = authorizedWrite.async { implicit request =>
    val username = request.retrieval

    withGithubContentFromCache(filename) { githubContent =>
      deployGithubContent(Username.fromRetrieval(username), filename, githubContent)
    }.fold(error => BadRequest(error), identity)
  }

  private def getTemplateVersion(json: CJson): Either[String, Int] =
    json.hcursor
      .downField("version")
      .as[Int]
      .leftMap(_.getMessage)

  private def getHandlebarsPayloadIds(json: CJson): Either[String, List[String]] =
    json.hcursor.downField("destinations").focus.flatMap { focus =>
      focus.asArray.map { arr =>
        arr.flatMap { obj =>
          val cursor = obj.hcursor
          for {
            id <- cursor.downField("id").as[String].toOption
            handlebarPayload <- cursor
                                  .downField("handlebarPayload")
                                  .as[Boolean]
                                  .toOption
                                  .orElse(Some(false))
            destinationType  <- cursor.downField("type").as[String].toOption
            dataOutputFormat <- cursor.downField("dataOutputFormat").as[String].toOption.orElse(Some(""))
            if destinationType === "handlebarsHttpApi" || handlebarPayload || dataOutputFormat === "hbs"
          } yield id
        }
      }
    } match {
      case Some(ids) if ids.nonEmpty => Right(ids.toList)
      case _                         => Right(Nil)
    }

  private def getHandlebarsSchemaIds(json: CJson): Either[String, List[String]] =
    json.hcursor.downField("destinations").focus.flatMap { focus =>
      focus.asArray.map { arr =>
        arr.flatMap { obj =>
          val cursor = obj.hcursor
          for {
            id <- cursor.downField("id").as[String].toOption
            validateHandlebarPayload <- cursor
                                          .downField("validateHandlebarPayload")
                                          .as[Boolean]
                                          .toOption
                                          .orElse(Some(false))
            if validateHandlebarPayload
          } yield id
        }
      }
    } match {
      case Some(ids) if ids.nonEmpty => Right(ids.toList)
      case _                         => Right(Nil)
    }

  def deploymentExisting(
    formTemplateId: FormTemplateId,
    filename: Filename
  ) =
    authorizedWrite.async { implicit request =>
      withLastCommit { lastCommitCheck =>
        withGithubContentFromCache(filename) { githubContent =>
          val mongoContent = githubContent.path match {
            case GithubPath.RootPath             => gformService.getFormTemplate(formTemplateId)
            case GithubPath.HandlebarsPath       => gformService.getRawHandlebarsTemplate(formTemplateId)
            case GithubPath.HandlebarsSchemaPath => gformService.getHandlebarsSchema(formTemplateId)
          }

          val downloadAction = githubContent.path match {
            case GithubPath.RootPath =>
              routes.DeploymentController.download(formTemplateId)
            case GithubPath.HandlebarsPath =>
              routes.DeploymentController.downloadHandlebarsTemplate(formTemplateId)
            case GithubPath.HandlebarsSchemaPath =>
              routes.DeploymentController.downloadHandlebarsSchema(formTemplateId)
          }
          val downloadLink =
            uk.gov.hmrc.eeittadminfrontend.views.html.deployment_link_download_gform(formTemplateId, downloadAction)
          (
            EitherT(mongoContent),
            githubService.getCommit(githubContent.commitSha).mapK(ioToFuture),
            EitherT.right[String](deploymentService.find(formTemplateId)),
            EitherT.liftF[Future, String, Either[String, Unit]](
              formTemplateValidator.validate(githubContent.content.jsonContent)
            ),
            EitherT(
              gformService
                .retrieveContentsForHandlebars(formTemplateId, cachingService.githubContents, githubContent.path)
            )
          ).parMapN {
            case (
                  mongoTemplate,
                  commit,
                  deploymentRecords,
                  validationResult,
                  (mongoTemplateHandlebars, githubContentHandlebars)
                ) =>
              val formActionOrError: Either[Call, String] = if (githubContent.path === GithubPath.RootPath) {
                val maybeMongoVersion: Option[Int] = getTemplateVersion(mongoTemplate.content.jsonContent).toOption

                val maybeGithubVersion: Option[Int] = getTemplateVersion(githubContent.content.jsonContent).toOption

                (maybeMongoVersion, maybeGithubVersion) match {
                  case (Some(mongoVersion), Some(githubVersion)) if mongoVersion === githubVersion =>
                    Left(
                      uk.gov.hmrc.eeittadminfrontend.controllers.routes.DeploymentController
                        .confirmNoVersionChange(formTemplateId, filename)
                    )
                  case (Some(mongoVersion), Some(githubVersion)) if mongoVersion + 1 === githubVersion =>
                    Left(
                      uk.gov.hmrc.eeittadminfrontend.controllers.routes.DeploymentController
                        .confirmAllowOldVersionJourney(formTemplateId, filename)
                    )
                  case (Some(mongoVersion), githubVersion) =>
                    val githubVersionDescription =
                      githubVersion.fold("missing in the json template")(version => s"$version instead")
                    Right(
                      s"Github versions needs to be $mongoVersion or ${mongoVersion + 1}. But it is $githubVersionDescription. Deployment is disabled until this mismatch is resolved."
                    )

                  case (None, _) =>
                    Right("Mongo template needs to have a version")
                }
              } else {
                Left(uk.gov.hmrc.eeittadminfrontend.controllers.routes.DeploymentController.deployFilename(filename))
              }

              val validationWarning: Option[String] = validationResult.swap.toOption
              val diff = DiffMaker.getDiff(filename, mongoTemplate, githubContent, diffConfig.timeout)
              val inSync = DiffMaker.inSync(mongoTemplate, githubContent)
              val diffHtml = uk.gov.hmrc.eeittadminfrontend.views.html.deployment_diff(Html(diff))
              val reconciliationLookup: ReconciliationLookup =
                toReconciliationLookup(mongoTemplateHandlebars, githubContentHandlebars)

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
                  reconciliationLookup,
                  formActionOrError
                )
              )
          }
        }
      }
    }

  def confirmNoVersionChange(formTemplateId: FormTemplateId, filename: Filename) =
    authorizedWrite.async { implicit request =>
      Ok(deployment_confirm_no_version_change(formTemplateId, filename)).pure[Future]
    }

  def confirmAllowOldVersionJourney(formTemplateId: FormTemplateId, filename: Filename) =
    authorizedWrite.async { implicit request =>
      Ok(deployment_confirm_allow_old_version_journey(formTemplateId, filename)).pure[Future]
    }

  def delete(formTemplateId: FormTemplateId) =
    authorizedDelete.async { implicit request =>
      val username = request.retrieval.value
      logger.info(s"$username is deleting ${formTemplateId.value}")
      gformService.deleteTemplate(formTemplateId).map { deleteResults =>
        logger.info(s"$username deleted ${formTemplateId.value}: $deleteResults")
        Ok(Json.toJson(deleteResults))
      }
    }

  def deleteHandlebarsTemplate(formTemplateId: FormTemplateId) =
    authorizedDelete.async { implicit request =>
      val username = request.retrieval.value
      logger.info(s"$username is deleting ${formTemplateId.value}")
      gformService.deleteHandlebarsTemplate(formTemplateId).map { deleteResult =>
        logger.info(s"$username deleted ${formTemplateId.value}: $deleteResult")
        Ok(Json.toJson(deleteResult))
      }
    }

  def deleteHandlebarsSchema(formTemplateId: FormTemplateId) =
    authorizedDelete.async { implicit request =>
      val username = request.retrieval.value
      logger.info(s"$username is deleting ${formTemplateId.value}")
      gformService.deleteHandlebarsSchema(formTemplateId).map { deleteResult =>
        logger.info(s"$username deleted ${formTemplateId.value}: $deleteResult")
        Ok(Json.toJson(deleteResult))
      }
    }

  def deploymentDeleted(formTemplateId: FormTemplateId, githubPath: GithubPath) = authorizedDelete.async {
    implicit request =>
      val downloadLink = githubPath match {
        case GithubPath.RootPath =>
          uk.gov.hmrc.eeittadminfrontend.views.html
            .deployment_link_download_gform(formTemplateId, routes.DeploymentController.download(formTemplateId))
        case GithubPath.HandlebarsPath =>
          uk.gov.hmrc.eeittadminfrontend.views.html
            .deployment_link_download_gform(
              formTemplateId,
              routes.DeploymentController.downloadHandlebarsTemplate(formTemplateId)
            )
        case GithubPath.HandlebarsSchemaPath =>
          uk.gov.hmrc.eeittadminfrontend.views.html
            .deployment_link_download_gform(
              formTemplateId,
              routes.DeploymentController.downloadHandlebarsSchema(formTemplateId)
            )
      }

      val deleteAction = githubPath match {
        case GithubPath.RootPath =>
          uk.gov.hmrc.eeittadminfrontend.controllers.routes.DeploymentController.delete(formTemplateId)
        case GithubPath.HandlebarsPath =>
          uk.gov.hmrc.eeittadminfrontend.controllers.routes.DeploymentController
            .deleteHandlebarsTemplate(formTemplateId)
        case GithubPath.HandlebarsSchemaPath =>
          uk.gov.hmrc.eeittadminfrontend.controllers.routes.DeploymentController.deleteHandlebarsSchema(formTemplateId)
      }
      Ok(deployment_deleted(formTemplateId, downloadLink, deleteAction)).pure[Future]
  }

  def deploymentNew(
    formTemplateId: FormTemplateId,
    filename: Filename,
    sha: BlobSha
  ) =
    authorizedWrite.async { implicit request =>
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

  def refreshCache(redirectUrl: RedirectUrl) = authorizedRead.async { request =>
    cachingService.refreshCache
    Redirect(redirectUrl.get(OnlyRelative).url).pure[Future]
  }

  def deploymentHome = authorizedRead.async { implicit request =>
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
    authorizedRead.async { implicit request =>
      val username = request.retrieval.value
      withLastCommit { lastCommitCheck =>
        for {
          mongoTemplateIds <- EitherT(gformService.getAllGformsTemplates)
          mongoHandlebarsIds <- {
            logger.info(s"$username Loading ${mongoTemplateIds.size} templates from MongoDB")
            EitherT(gformService.getAllHandlebarsTemplates)
          }
          mongoHandlebarsSchemaIds <- {
            logger.info(s"$username Loading ${mongoHandlebarsIds.size} handlebar templates from MongoDB")
            EitherT(gformService.getAllHandlebarsSchemas)
          }
          mongoContentsForTemplate <- {
            logger.info(s"$username Loading ${mongoHandlebarsSchemaIds.size} handlebar schemas from MongoDB")
            mongoTemplateIds.parTraverse(formTemplateId => EitherT(gformService.getFormTemplate(formTemplateId)))
          }
          mongoContentsForHandlebars <-
            mongoHandlebarsIds.parTraverse(id => EitherT(gformService.getRawHandlebarsTemplate(id)))
          mongoContentsForHandlebarsSchema <-
            mongoHandlebarsSchemaIds.parTraverse(id => EitherT(gformService.getHandlebarsSchema(id)))
          mongoTemplates = mongoContentsForTemplate ++ mongoContentsForHandlebars ++ mongoContentsForHandlebarsSchema
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

    val mongoLookup: Map[FullPath, MongoContent] =
      mongoTemplates.map(a => a.fullPath -> a).toMap

    val reconciliations: List[Reconciliation] = githubTemplates.map { case (filename, githubContent) =>
      reconciliation(
        mongoLookup,
        githubContent,
        filename
      )
    }

    val n: List[Reconciliation.New] = reconciliations.collect { case r: Reconciliation.New => r }
    val e: List[Reconciliation.Existing] = reconciliations.collect { case r: Reconciliation.Existing => r }

    val allGithubFullPaths: List[FullPath] =
      reconciliations.map(r => FullPath(s"${asPath(r.path)}${r.formTemplateId.value}"))
    val deletedTemplates: List[Reconciliation.Deleted] =
      mongoLookup.keys.toList
        .filter(fPath => !allGithubFullPaths.contains_(fPath))
        .sortBy(_.value)
        .map(fullPath =>
          mongoLookup.get(fullPath).map(_.path) match {
            case Some(GithubPath.RootPath) =>
              val formTemplateId = FormTemplateId(fullPath.value)
              Reconciliation
                .Deleted(
                  formTemplateId,
                  GithubPath.RootPath,
                  routes.DeploymentController.deploymentDeleted(formTemplateId, GithubPath.RootPath)
                )
            case Some(GithubPath.HandlebarsPath) =>
              val formTemplateId = FormTemplateId(fullPath.value.replaceFirst(asPath(GithubPath.HandlebarsPath), ""))
              Reconciliation
                .Deleted(
                  formTemplateId,
                  GithubPath.HandlebarsPath,
                  routes.DeploymentController.deploymentDeleted(formTemplateId, GithubPath.HandlebarsPath)
                )
            case Some(GithubPath.HandlebarsSchemaPath) =>
              val formTemplateId = FormTemplateId(fullPath.value.replaceFirst(asPath(GithubPath.HandlebarsPath), ""))
              Reconciliation
                .Deleted(
                  formTemplateId,
                  GithubPath.HandlebarsSchemaPath,
                  routes.DeploymentController.deploymentDeleted(formTemplateId, GithubPath.HandlebarsSchemaPath)
                )
            case None => throw new RuntimeException(s"Content not found for form template: ${fullPath.value}")
          }
        )
    ReconciliationLookup(n.groupBy(_.formTemplateId), e.groupBy(_.formTemplateId), deletedTemplates)
  }

  private def reconciliation(
    mongoLookup: Map[FullPath, MongoContent],
    githubContent: GithubContent,
    filename: Filename
  ): Reconciliation = {
    val formTemplateId = githubContent.formTemplateId
    val fullPath = githubContent.fullPath
    mongoLookup.get(fullPath) match {
      case None =>
        Reconciliation.New(
          formTemplateId,
          githubContent.path,
          filename,
          routes.DeploymentController.deploymentNew(
            formTemplateId,
            filename,
            githubContent.blobSha
          )
        )
      case Some(mongoContent) =>
        if (mongoContent.path === githubContent.path) {
          val inSync = DiffMaker.inSync(mongoContent, githubContent)
          Reconciliation.Existing(
            formTemplateId,
            githubContent.path,
            filename,
            routes.DeploymentController
              .deploymentExisting(formTemplateId, filename),
            inSync
          )
        } else {
          Reconciliation.New(
            formTemplateId,
            githubContent.path,
            filename,
            routes.DeploymentController.deploymentNew(
              formTemplateId,
              filename,
              githubContent.blobSha
            )
          )
        }
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
