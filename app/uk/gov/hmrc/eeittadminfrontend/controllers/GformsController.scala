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

import akka.stream.Materializer
import akka.stream.scaladsl.{ FileIO, Framing, Keep, Sink, StreamConverters }
import akka.util.ByteString
import cats.data.EitherT
import cats.syntax.all._
import io.circe.{ DecodingFailure, ParsingFailure }
import julienrf.json.derived
import org.apache.commons.codec.binary.Base64
import org.slf4j.{ Logger, LoggerFactory }
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.I18nSupport
import play.api.libs.json._
import play.api.mvc.{ AnyContent, MessagesControllerComponents, Request, Result }
import uk.gov.hmrc.eeittadminfrontend.auth.AuthConnector
import uk.gov.hmrc.eeittadminfrontend.config.{ AppConfig, AuthAction, RequestWithUser }
import uk.gov.hmrc.eeittadminfrontend.connectors.GformConnector
import uk.gov.hmrc.eeittadminfrontend.deployment.{ Filename, GithubContent }
import uk.gov.hmrc.eeittadminfrontend.models.github.Authorization
import uk.gov.hmrc.eeittadminfrontend.models._
import uk.gov.hmrc.eeittadminfrontend.services.{ CachingService, GformService }
import uk.gov.hmrc.eeittadminfrontend.utils.DateUtils
import uk.gov.hmrc.eeittadminfrontend.validators.FormTemplateValidator
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import java.io.{ BufferedOutputStream, ByteArrayInputStream, ByteArrayOutputStream }
import java.time.Instant
import java.util.zip.{ ZipEntry, ZipOutputStream }
import scala.concurrent.{ ExecutionContext, Future }
sealed trait RefreshTemplateResult extends Product with Serializable
case class RefreshSuccesful(formTemplateId: FormTemplateId) extends RefreshTemplateResult
case class RefreshError(formTemplateId: FormTemplateId, errorMessage: String) extends RefreshTemplateResult

object RefreshTemplateResult {
  implicit val format: OFormat[RefreshTemplateResult] = derived.oformat()
}

sealed trait RefreshResult extends Product with Serializable

object RefreshResult {
  implicit val format: OFormat[RefreshResult] = derived.oformat()
}

case class RefreshTemplateResults(results: List[RefreshTemplateResult]) extends RefreshResult {
  def addResult(rtr: RefreshTemplateResult) = RefreshTemplateResults(rtr :: results)
}
object RefreshTemplateResults {
  val empty = RefreshTemplateResults(List.empty[RefreshTemplateResult])
}
case object NoTempatesToRefresh extends RefreshResult

class GformsController(
  val authConnector: AuthConnector,
  authAction: AuthAction,
  gformConnector: GformConnector,
  gformService: GformService,
  formTemplateValidator: FormTemplateValidator,
  messagesControllerComponents: MessagesControllerComponents,
  cachingService: CachingService,
  authorization: Authorization
)(implicit ec: ExecutionContext, appConfig: AppConfig, m: Materializer)
    extends FrontendController(messagesControllerComponents) with I18nSupport {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  private def fileByteData(fileList: Seq[(FormTemplateId, JsValue)]): ByteArrayInputStream = {

    val baos = new ByteArrayOutputStream()
    val zos = new ZipOutputStream(new BufferedOutputStream(baos))

    try fileList.foreach { case (formTemplateId, formTemplate) =>
      zos.putNextEntry(new ZipEntry(formTemplateId.value + ".json"))
      zos.write(Json.prettyPrint(formTemplate).getBytes())
      zos.closeEntry()
    } finally zos.close()

    new ByteArrayInputStream(baos.toByteArray)
  }

  def getBlob =
    authAction.async { implicit request =>
      logger.info(s" ${request.userData} ask for all templates as a zip blob")
      val blobFuture: Future[Seq[(FormTemplateId, JsValue)]] =
        gformConnector.getAllGformsTemplates.flatMap {
          case JsArray(templates) =>
            val formTemplateIds = templates.collect {
              case JsString(template) if !template.startsWith("specimen-") => FormTemplateId(template)
            }
            Future.traverse(formTemplateIds) { formTemplateId =>
              gformConnector
                .getGformsTemplate(formTemplateId)
                .map {
                  case Right(formTemplate) => (formTemplateId, formTemplate)
                  case Left(error)         => (formTemplateId, JsString(error.error))
                }
            }
          case _ => Future.successful(Seq.empty)
        }
      blobFuture.map { blob =>
        val now = Instant.now()
        Ok.chunked(StreamConverters.fromInputStream(() => fileByteData(blob)))
          .withHeaders(
            CONTENT_TYPE -> "application/zip",
            CONTENT_DISPOSITION -> s"""attachment; filename = "gform-prod-blob-${DateUtils.formatInstantNoSpace(
              now
            )}.zip""""
          )
      }
    }

  def getGformByFormType =
    authAction.async { implicit request =>
      gFormForm
        .bindFromRequest()
        .fold(
          formWithErrors =>
            Future.successful(BadRequest(uk.gov.hmrc.eeittadminfrontend.views.html.gform_page(gFormForm))),
          gformIdAndVersion => {
            logger.info(s"${request.userData} Queried for ${gformIdAndVersion.formTemplateId}")
            gformConnector.getGformsTemplate(gformIdAndVersion.formTemplateId).map {
              case Left(ex) =>
                Ok(s"Problem when fetching form template: ${gformIdAndVersion.formTemplateId}. Reason: $ex")
              case Right(r) => Ok(Json.prettyPrint(r))
            }
          }
        )
    }

  def saveGformSchema =
    authAction.async(parse.multipartFormData) { implicit request =>
      val rawJson = new String(Base64.decodeBase64(request.body.dataParts("template").mkString), "UTF-8")

      val result: EitherT[Future, String, Result] =
        for {
          formTemplate   <- parseRawJson(rawJson).leftMap(e => "Not a valid json: " + e.getMessage + "\n\n" + rawJson)
          _              <- EitherT(formTemplateValidator.validate(formTemplate))
          formTemplateId <- getFormTemplateId(formTemplate).leftMap(decodingFailure => "No '_id' field defined.")
          _              <- saveTemplate(formTemplateId, formTemplate)
        } yield {
          logger.info(s"${request.userData} saved ID: ${formTemplateId.value}")
          Ok("Saved")
        }

      result
        .leftMap(error => Redirect(routes.GformsController.gformPage()).flashing("error" -> error))
        .value
        .map(_.merge)
    }

  private def saveTemplate(formTemplateId: FormTemplateId, formTemplate: io.circe.Json) =
    gformService.saveTemplate(formTemplateId, formTemplate)

  private def parseRawJson(rawJson: String): EitherT[Future, ParsingFailure, io.circe.Json] =
    EitherT.fromEither[Future](io.circe.parser.parse(rawJson))

  private def getFormTemplateId(
    formTemplate: io.circe.Json
  ): EitherT[Future, DecodingFailure, FormTemplateId] =
    EitherT.fromEither[Future](formTemplate.hcursor.downField("_id").as[String].map(FormTemplateId.apply))

  def getAllTemplates =
    authAction.async { implicit request =>
      logger.info(s"${request.userData} Queried for all form templates")
      gformConnector.getAllGformsTemplates.map(x => Ok(x))
    }

  def reloadTemplates =
    authAction.async { implicit request =>
      logger.info(s"${request.userData} Reload all form templates")

      for {
        maybeTemplateIds <- gformConnector.getAllGformsTemplates.map(_.as[List[FormTemplateId]])
        result           <- fetchAndSave(maybeTemplateIds.filterNot(_.value.startsWith("specimen-")))
      } yield Ok(Json.toJson(result))
    }

  def fetchAndSave(
    formTemplateIds: List[FormTemplateId]
  )(implicit request: RequestWithUser[AnyContent]): Future[RefreshResult] =
    formTemplateIds.foldLeft(Future.successful(RefreshTemplateResults.empty)) { case (resultsAcc, formTemplateId) =>
      logger.info(s"${request.userData} Refreshing formTemplateId: $formTemplateId")
      for {
        results         <- resultsAcc
        templateOrError <- gformConnector.getGformsTemplate(formTemplateId)
        result <- templateOrError match {
                    case Left(error)     => Future.successful(Left(error.error))
                    case Right(template) => gformConnector.saveTemplate(formTemplateId, template)
                  }
      } yield {
        logger.info(s"${request.userData} Refreshing formTemplateId: $formTemplateId finished: " + result)
        result match {
          case Left(error) => results.addResult(RefreshError(formTemplateId, error))
          case Right(())   => results.addResult(RefreshSuccesful(formTemplateId))
        }
      }
    }

  def getAllSchema =
    authAction.async { implicit request =>
      logger.info(s"${request.userData} Queried for all form Schema")
      gformConnector.getAllSchema.map(x => Ok(x))
    }

  def deleteGformTemplate =
    authAction.async { implicit request =>
      gFormForm
        .bindFromRequest()
        .fold(
          formWithErrors =>
            Future.successful(BadRequest(uk.gov.hmrc.eeittadminfrontend.views.html.gform_page(gFormForm))),
          gformId => {
            logger.info(s"${request.userData} deleted ${gformId.formTemplateId} ")
            gformConnector.deleteTemplate(gformId.formTemplateId)
          }
        )
    }

  def gformPage =
    authAction.async { implicit request =>
      Future.successful(Ok(uk.gov.hmrc.eeittadminfrontend.views.html.gform_page(gFormForm)))
    }

  private def getFilterList(filters: String) = filters.split(",").toList

  def gformFormTemplatesWithPIIInTitleHome() = authAction.async { implicit request =>
    Future.successful(
      Ok(
        uk.gov.hmrc.eeittadminfrontend.views.html
          .gform_formtemplates_pii_home(
            List.empty,
            Some("name,email,business,auth."),
            authorization,
            cachingService.cacheStatus
          )
      )
    )
  }

  def gformFormTemplatesWithPIIInTitle = authAction.async { implicit request =>
    def doFetchPIIInTitleDetails(
      formTemplateId: FormTemplateId,
      filters: String,
      templateSource: TemplateSource
    ): Future[Either[String, Option[JsonWithPIIDetails]]] =
      fetchPIIInTitleDetails(formTemplateId, filters, templateSource)
        .map(Right(_))
        .recover { case e =>
          Left(e.getMessage)
        }

    gformFormTemplatesWithPIIInTitleForm
      .bindFromRequest()
      .fold(
        error => {
          logger.error("Failed to bind request to form for gformFormTemplatesWithPIIInTitle", error)
          Future.successful(Redirect(routes.GformsController.gformFormTemplatesWithPIIInTitleHome()))
        },
        form =>
          cachingService.githubContents
            .map { case (filename, content) =>
              for {
                github <- doFetchPIIInTitleDetails(content.formTemplateId, form.filters, Github)
                mongo  <- doFetchPIIInTitleDetails(content.formTemplateId, form.filters, Mongo)
              } yield FormTemplateWithPIIInTitle(
                filename,
                content.formTemplateId,
                github.fold(_ => None, _.map(_.list.size)),
                mongo.fold(_ => None, _.map(_.list.size)),
                github.fold(err => List(err), _ => List.empty) ++ mongo.fold(err => List(err), _ => List.empty)
              )
            }
            .sequence
            .map(formTemplatesWithPII =>
              Ok(
                uk.gov.hmrc.eeittadminfrontend.views.html
                  .gform_formtemplates_pii(
                    formTemplatesWithPII.sortBy(f => (f.githubPIICount, f.mongoPIICount)).reverse,
                    form.filters,
                    authorization
                  )
              )
            )
            .recover { case e =>
              InternalServerError("Failed to gformFormTemplatesWithPIIInTitle: " + e)
            }
      )
  }

  def gformFormTemplateWithPIIInTitleHome(
    formTemplateId: FormTemplateId,
    filters: String,
    templateSource: TemplateSource
  ) = authAction.async { implicit request =>
    getFormTemplatePIIDetails(formTemplateId, filters, templateSource)
      .fold(Future.successful(BadRequest(s"Form template with id $formTemplateId not found"))) {
        _.map { formTemplateWithPIIInTitleDetails =>
          Ok(
            uk.gov.hmrc.eeittadminfrontend.views.html
              .gform_formtemplate_pii(
                formTemplateWithPIIInTitleDetails,
                authorization,
                formTemplateId,
                filters
              )
          )
        }
      }
  }

  def gformFormTemplateWithPIIInTitle = authAction.async { implicit request =>
    gformFormTemplateWithPIIInTitleForm
      .bindFromRequest()
      .fold(
        _ => Future.successful(Redirect(routes.GformsController.gformFormTemplatesWithPIIInTitleHome())),
        form =>
          getFormTemplatePIIDetails(form.formTemplateId, form.filters, form.templateSource)
            .fold(Future.successful(BadRequest(s"Form template with id ${form.formTemplateId} not found"))) {
              _.map { formTemplateWithPIIInTitleDetails =>
                Ok(
                  uk.gov.hmrc.eeittadminfrontend.views.html
                    .gform_formtemplate_pii(
                      formTemplateWithPIIInTitleDetails,
                      authorization,
                      form.formTemplateId,
                      form.filters
                    )
                )
              }
            }
      )
  }

  private def getFormTemplatePIIDetails(
    formTemplateId: FormTemplateId,
    filters: String,
    templateSource: TemplateSource
  )(implicit
    request: Request[AnyContent]
  ): Option[Future[FormTemplateWithPIIInTitleDetails]] =
    cachingService.githubContents
      .find { case (_, content) =>
        content.formTemplateId == formTemplateId
      }
      .map { case (filename, content) =>
        fetchPIIInTitleDetails(content.formTemplateId, filters, templateSource)
          .map { jsonWithPIIDetails =>
            FormTemplateWithPIIInTitleDetails(
              filename,
              jsonWithPIIDetails.fold("")(_.json),
              formTemplateId,
              jsonWithPIIDetails.fold[List[PIIDetails]](List.empty)(_.list)
            )
          }
      }

  case class JsonWithPIIDetails(json: String, list: List[PIIDetails])

  private def fetchPIIInTitleDetails(
    formTemplateId: FormTemplateId,
    filters: String,
    source: TemplateSource
  )(implicit request: Request[AnyContent]): Future[Option[JsonWithPIIDetails]] =
    source match {
      case Github =>
        cachingService.githubContents
          .find { case (_, content) =>
            content.formTemplateId == formTemplateId
          }
          .fold(Future.successful[Option[JsonWithPIIDetails]](None)) { case (_: Filename, content: GithubContent) =>
            val json = content.json.toString
            gformConnector
              .getTitlesWithPII(json, getFilterList(filters))
              .map(pii => Option(JsonWithPIIDetails(json, pii)))
          }
      case Mongo =>
        gformConnector
          .getGformsTemplate(formTemplateId)
          .flatMap {
            case Right(json) =>
              val jsonStr = Json.prettyPrint(json)
              gformConnector
                .getTitlesWithPII(jsonStr, getFilterList(filters))
                .map(pii => Option(JsonWithPIIDetails(jsonStr, pii)))
            case Left(errorResponse) =>
              if (errorResponse.status.contains(404)) // ignore notfound errors
                Future.successful[Option[JsonWithPIIDetails]](None)
              else
                throw new RuntimeException("Failed to get form template from gform: " + errorResponse.error)
          }
    }

  def gformAnalytics =
    authAction.async { implicit request =>
      Future.successful(Ok(uk.gov.hmrc.eeittadminfrontend.views.html.analytics()))
    }

  def dbLookupFileUpload() =
    authAction.async(parse.multipartFormData) { implicit request =>
      val collectionName = request.body.dataParts("collectionName").head
      if (collectionName.isEmpty)
        Future.successful(BadRequest("'collectionName' param is empty"))
      else {
        logger.info(s"Uploading db lookup file for collection $collectionName")
        request.body
          .file("file")
          .map { filePart =>
            FileIO
              .fromPath(filePart.ref.getAbsoluteFile.toPath)
              .via(
                Framing
                  .delimiter(ByteString("\n"), 100, true)
                  .grouped(1000)
              )
              .mapAsync(1)((lines: Seq[ByteString]) =>
                gformConnector.saveDBLookupIds(
                  collectionName,
                  lines.map(_.utf8String).filter(_.trim().nonEmpty).map(DbLookupId.apply)
                )
              )
              .toMat(Sink.ignore)(Keep.right)
              .run()
              .map { _ =>
                Created(s"Uploaded db lookup file for collection $collectionName")
              }
              .recover { case e =>
                logger.info(s"Failed to send uploaded file to gforms for collection $collectionName", e)
                InternalServerError(
                  s"Failed to send uploaded file to gforms for collection $collectionName [error=$e]"
                )
              }
          }
          .getOrElse(Future.successful(BadRequest("'file' param is missing")))
      }
    }

  val gFormForm: Form[GformId] = Form(
    mapping("formTemplateId" -> mapping("value" -> text)(FormTemplateId.apply)(FormTemplateId.unapply))(GformId.apply)(
      GformId.unapply
    )
  )

  val gformFormTemplatesWithPIIInTitleForm: Form[FormTemplatesWithPIIInTitleForm] = Form(
    mapping("filters" -> text)(FormTemplatesWithPIIInTitleForm.apply)(
      FormTemplatesWithPIIInTitleForm.unapply
    )
  )

  val gformFormTemplateWithPIIInTitleForm: Form[FormTemplateWithPIIInTitleForm] = Form(
    mapping(
      "filters"        -> text,
      "formTemplateId" -> mapping("value" -> text)(FormTemplateId.apply)(FormTemplateId.unapply),
      "templateSource" -> nonEmptyText.transform[TemplateSource](TemplateSource.fromString, _.toString)
    )(FormTemplateWithPIIInTitleForm.apply)(FormTemplateWithPIIInTitleForm.unapply)
  )
}
