/*
 * Copyright 2020 HM Revenue & Customs
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

import java.io.{ BufferedOutputStream, ByteArrayInputStream, ByteArrayOutputStream }
import java.time.format.DateTimeFormatter
import java.time.{ Instant, ZoneId }
import java.util.Locale
import java.util.zip.{ ZipEntry, ZipOutputStream }

import akka.stream.scaladsl.StreamConverters
import julienrf.json.derived
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.I18nSupport
import play.api.libs.json._
import play.api.mvc.{ AnyContent, MessagesControllerComponents }
import uk.gov.hmrc.eeittadminfrontend.auth.AuthConnector
import uk.gov.hmrc.eeittadminfrontend.config.{ AppConfig, Authentication, RequestWithUser }
import uk.gov.hmrc.eeittadminfrontend.connectors.GformConnector
import uk.gov.hmrc.eeittadminfrontend.models.{ FormTemplateId, GformId }
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

import scala.concurrent.{ ExecutionContext, Future }

sealed trait RefreshTemplateResult extends Product with Serializable
case class RefreshSuccesful(formTemplateId: FormTemplateId) extends RefreshTemplateResult
case class RefreshError(formTemplateId: FormTemplateId, errorMessage: String) extends RefreshTemplateResult

object RefreshTemplateResult {
  implicit val format: OFormat[RefreshTemplateResult] = derived.oformat
}

sealed trait RefreshResult extends Product with Serializable

object RefreshResult {
  implicit val format: OFormat[RefreshResult] = derived.oformat
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
  gformConnector: GformConnector,
  messagesControllerComponents: MessagesControllerComponents)(implicit ec: ExecutionContext, appConfig: AppConfig)
    extends FrontendController(messagesControllerComponents) with I18nSupport {

  private def fileByteData(fileList: Seq[(FormTemplateId, JsValue)]): ByteArrayInputStream = {

    val baos = new ByteArrayOutputStream()
    val zos = new ZipOutputStream(new BufferedOutputStream(baos))

    try {
      fileList.foreach {
        case (formTemplateId, formTemplate) => {
          zos.putNextEntry(new ZipEntry(formTemplateId.value + ".json"))
          zos.write(Json.prettyPrint(formTemplate).getBytes())
          zos.closeEntry()
        }
      }
    } finally {
      zos.close()
    }

    new ByteArrayInputStream(baos.toByteArray)
  }

  def getBlob = Authentication.async { implicit request =>
    Logger.info(s" ${request.userLogin} ask for all templates as a zip blob")
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
                case Left(error)         => (formTemplateId, JsString(error))
              }
          }
        case _ => Future.successful(Seq.empty)
      }
    blobFuture.map { blob =>
      val now = Instant.now()
      Ok.chunked(StreamConverters.fromInputStream(() => fileByteData(blob)))
        .withHeaders(
          CONTENT_TYPE        -> "application/zip",
          CONTENT_DISPOSITION -> s"""attachment; filename = "gform-prod-blob-${formatInstant(now)}.zip""""
        )
    }
  }

  private val dtf = DateTimeFormatter
    .ofPattern("dd-MM-yyyy-HH:mm:ss")
    .withLocale(Locale.UK)
    .withZone(ZoneId.of("Europe/London"))

  private def formatInstant(instant: Instant): String = dtf.format(instant)

  def getGformByFormType = Authentication.async { implicit request =>
    gFormForm
      .bindFromRequest()
      .fold(
        formWithErrors => {
          Future.successful(BadRequest(uk.gov.hmrc.eeittadminfrontend.views.html.gform_page(gFormForm)))
        },
        gformIdAndVersion => {
          Logger.info(s"${request.userLogin} Queried for ${gformIdAndVersion.formTemplateId}")
          gformConnector.getGformsTemplate(gformIdAndVersion.formTemplateId).map {
            case Left(ex) =>
              Ok(s"Problem when fetching form template: ${gformIdAndVersion.formTemplateId}. Reason: $ex")
            case Right(r) => Ok(Json.prettyPrint(r))
          }
        }
      )
  }

  def saveGformSchema = Authentication.async(parse.multipartFormData) { implicit request =>
    val template = Json.parse(
      new String(
        org.apache.commons.codec.binary.Base64.decodeBase64(request.body.dataParts("template").mkString),
        "UTF-8"))
    gformConnector.saveTemplate(template).map { x =>
      {
        Logger.info(s"${request.userLogin} saved ID: ${template \ "_id"} }")
        Ok("Saved")
      }
    }
  }

  def getAllTemplates = Authentication.async { implicit request =>
    Logger.info(s"${request.userLogin} Queried for all form templates")
    gformConnector.getAllGformsTemplates.map(x => Ok(x))
  }

  def reloadTemplates = Authentication.async { implicit request =>
    Logger.info(s"${request.userLogin} Reload all form templates")

    for {
      maybeTemplateIds <- gformConnector.getAllGformsTemplates.map(_.as[List[FormTemplateId]])
      res              <- fetchAndSave(maybeTemplateIds.filterNot(_.value.startsWith("specimen-")))
    } yield Ok(Json.toJson(res))
  }

  def fetchAndSave(formTemplateIds: List[FormTemplateId])(
    implicit hc: HeaderCarrier,
    request: RequestWithUser[AnyContent]): Future[RefreshResult] =
    formTemplateIds.foldLeft(Future.successful(RefreshTemplateResults.empty)) {
      case (resultsAcc, formTemplateId) =>
        Logger.info(s"${request.userLogin} Refreshing formTemplateId: $formTemplateId")
        for {
          results         <- resultsAcc
          templateOrError <- gformConnector.getGformsTemplate(formTemplateId)
          res <- templateOrError match {
                  case Left(error)     => Future.successful(Left(error))
                  case Right(template) => gformConnector.saveTemplate(template)
                }
        } yield {
          Logger.info(s"${request.userLogin} Refreshing formTemplateId: $formTemplateId finished: " + res)
          res match {
            case Left(error) => results.addResult(RefreshError(formTemplateId, error))
            case Right(())   => results.addResult(RefreshSuccesful(formTemplateId))
          }
        }
    }

  def getAllSchema = Authentication.async { implicit request =>
    Logger.info(s"${request.userLogin} Queried for all form Schema")
    gformConnector.getAllSchema.map(x => Ok(x))
  }

  def deleteGformTemplate = Authentication.async { implicit request =>
    gFormForm
      .bindFromRequest()
      .fold(
        formWithErrors => {
          Future.successful(BadRequest(uk.gov.hmrc.eeittadminfrontend.views.html.gform_page(gFormForm)))
        },
        gformId => {
          Logger.info(s"${request.userLogin} deleted ${gformId.formTemplateId} ")
          gformConnector.deleteTemplate(gformId.formTemplateId).map(res => Ok)
        }
      )
  }

  def gformPage = Authentication.async { implicit request =>
    Future.successful(Ok(uk.gov.hmrc.eeittadminfrontend.views.html.gform_page(gFormForm)))
  }

  def gformAnalytics = Authentication.async { implicit request =>
    Future.successful(Ok(uk.gov.hmrc.eeittadminfrontend.views.html.analytics()))
  }

  val gFormForm: Form[GformId] = Form(
    mapping("formTemplateId" -> mapping("value" -> text)(FormTemplateId.apply)(FormTemplateId.unapply))(GformId.apply)(
      GformId.unapply)
  )
}
