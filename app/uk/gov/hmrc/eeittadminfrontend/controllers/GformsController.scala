/*
 * Copyright 2017 HM Revenue & Customs
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

import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{ I18nSupport, MessagesApi }
import play.api.libs.json.Json
import uk.gov.hmrc.eeittadminfrontend.AppConfig
import uk.gov.hmrc.eeittadminfrontend.config.Authentication
import uk.gov.hmrc.eeittadminfrontend.connectors.GformConnector
import uk.gov.hmrc.eeittadminfrontend.models.{ FormTypeId, GformId }
import uk.gov.hmrc.play.frontend.auth.Actions
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future

class GformsController(val authConnector: AuthConnector)(implicit appConfig: AppConfig, val messagesApi: MessagesApi) extends FrontendController with Actions with I18nSupport {

  def getGformByFormType = Authentication.async { implicit request =>
    gFormForm.bindFromRequest().fold(
      formWithErrors => {
        Future.successful(BadRequest(uk.gov.hmrc.eeittadminfrontend.views.html.gform_page(gFormForm)))
      },
      gformIdAndVersion => {
        Logger.info(s" ${request.session.get("token").get} Queried for ${gformIdAndVersion.formTypeId}")
        GformConnector.getGformsTemplate(gformIdAndVersion.formTypeId).map { x => Ok(Json.prettyPrint(x))
        }
      }
    )
  }

  def saveGformSchema = Authentication.async(parse.urlFormEncoded) { implicit request =>
    val template = Json.parse(request.body.apply("template").mkString)
    GformConnector.saveTemplate(template).map {
      x =>
        {
          Logger.info(s" ${request.session.get("token").get} saved ID: ${template \ "formTypeId"} version: ${template \ "version"}")
          Ok("Saved")
        }
    }
  }

  def getAllTemplates = Authentication.async { implicit request =>
    Logger.info(s"${request.session.get("token").get} Queried for all form templates")
    GformConnector.getAllGformsTemplates.map(x => Ok(x))
  }

  def getAllSchema = Authentication.async { implicit request =>
    Logger.info(s"${request.session.get("token").get} Queried for all form Schema")
    GformConnector.getAllSchema.map(x => Ok(x))
  }

  def deleteGformTemplate = Authentication.async { implicit request =>
    gFormForm.bindFromRequest().fold(
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

  def gformAuthor = Authentication.async { implicit request =>
    Future.successful(Ok(uk.gov.hmrc.eeittadminfrontend.views.html.author_tool()))
  }

  val gFormForm: Form[GformId] = Form(
    mapping(
      "formTypeId" -> mapping(
        "value" -> text
      )(FormTypeId.apply)(FormTypeId.unapply)
    )(GformId.apply)(GformId.unapply)
  )
}

