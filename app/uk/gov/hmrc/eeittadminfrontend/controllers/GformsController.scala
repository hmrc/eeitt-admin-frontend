/*
 * Copyright 2019 HM Revenue & Customs
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

import java.util.Base64

import julienrf.json.derived
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{ I18nSupport, MessagesApi }
import play.api.libs.json.{ Json, OFormat }
import uk.gov.hmrc.eeittadminfrontend.AppConfig
import uk.gov.hmrc.eeittadminfrontend.config.Authentication
import uk.gov.hmrc.eeittadminfrontend.connectors.GformConnector
import uk.gov.hmrc.eeittadminfrontend.models.{ FormTypeId, GformId }
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.frontend.auth.Actions
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future

sealed trait RefreshTemplateResult extends Product with Serializable
case class RefreshSuccesful(formTemplateId: FormTypeId) extends RefreshTemplateResult
case class RefreshError(formTemplateId: FormTypeId, errorMessage: String) extends RefreshTemplateResult

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

class GformsController(val authConnector: AuthConnector)(implicit appConfig: AppConfig, val messagesApi: MessagesApi)
    extends FrontendController with Actions with I18nSupport {

  def getGformByFormType = Authentication.async { implicit request =>
    gFormForm
      .bindFromRequest()
      .fold(
        formWithErrors => {
          Future.successful(BadRequest(uk.gov.hmrc.eeittadminfrontend.views.html.gform_page(gFormForm)))
        },
        gformIdAndVersion => {
          Logger.info(s" ${request.session.get("token").get} Queried for ${gformIdAndVersion.formTypeId}")
          GformConnector.getGformsTemplate(gformIdAndVersion.formTypeId).map {
            case Left(ex) =>
              Ok(s"Problem when fetching form template: ${gformIdAndVersion.formTypeId}. Reason: $ex")
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
    GformConnector.saveTemplate(template).map { x =>
      {
        Logger.info(s" ${request.session.get("token").get} saved ID: ${template \ "_id"} }")
        Ok("Saved")
      }
    }
  }

  def getAllTemplates = Authentication.async { implicit request =>
    Logger.info(s"${request.session.get("token").get} Queried for all form templates")
    GformConnector.getAllGformsTemplates.map(x => Ok(x))
  }

  def reloadTemplates = Authentication.async { implicit request =>
    Logger.info(s"${request.session.get("token").get} Reload all form templates")

    for {
      maybeTemplateIds <- GformConnector.getAllGformsTemplates.map(_.asOpt[List[FormTypeId]])
      res              <- fetchAndSave(maybeTemplateIds)
    } yield Ok(Json.toJson(res))
  }

  def fetchAndSave(maybeFormTemplateIds: Option[List[FormTypeId]])(implicit hc: HeaderCarrier): Future[RefreshResult] =
    maybeFormTemplateIds match {
      case None => Future.successful(NoTempatesToRefresh)
      case Some(formTemplateIds) =>
        formTemplateIds.foldLeft(Future.successful(RefreshTemplateResults.empty)) {
          case (resultsAcc, formTemplateId) =>
            for {
              results         <- resultsAcc
              templateOrError <- GformConnector.getGformsTemplate(formTemplateId)
              res <- templateOrError match {
                      case Left(error)     => Future.successful(Left(error))
                      case Right(template) => GformConnector.saveTemplate(template)
                    }
            } yield
              res match {
                case Left(error) => results.addResult(RefreshError(formTemplateId, error))
                case Right(())   => results.addResult(RefreshSuccesful(formTemplateId))
              }
        }
    }

  def getAllSchema = Authentication.async { implicit request =>
    Logger.info(s"${request.session.get("token").get} Queried for all form Schema")
    GformConnector.getAllSchema.map(x => Ok(x))
  }

  def deleteGformTemplate = Authentication.async { implicit request =>
    gFormForm
      .bindFromRequest()
      .fold(
        formWithErrors => {
          Future.successful(BadRequest(uk.gov.hmrc.eeittadminfrontend.views.html.gform_page(gFormForm)))
        },
        gformId => {
          Logger.info(s" ${request.session.get("token").get} deleted ${gformId.formTypeId} ")
          GformConnector.deleteTemplate(gformId.formTypeId).map(res => Ok)
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
    mapping("formTypeId" -> mapping("value" -> text)(FormTypeId.apply)(FormTypeId.unapply))(GformId.apply)(
      GformId.unapply)
  )
}
