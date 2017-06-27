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
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.eeittadminfrontend.AppConfig
import uk.gov.hmrc.eeittadminfrontend.config.Authentication
import uk.gov.hmrc.eeittadminfrontend.connectors.GformConnector
import uk.gov.hmrc.eeittadminfrontend.models.{FormTypeId, GformIdAndVersion, GformTemplate}
import uk.gov.hmrc.play.frontend.auth.Actions
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future

class GformsController(val authConnector: AuthConnector)(implicit appConfig: AppConfig, val messagesApi: MessagesApi) extends FrontendController with Actions with I18nSupport {

  def getGformByFormType = Authentication.async { implicit request =>
    gFormForm.bindFromRequest().fold(
      formWithErrors => {
        Future.successful(BadRequest(uk.gov.hmrc.eeittadminfrontend.views.html.gform_page(formWithErrors, gFormSchema)))
      },
      gformIdAndVersion =>
        GformConnector.getGformsTemplate(gformIdAndVersion.formTypeId, gformIdAndVersion.version).map { x =>
          x match {
            case Some(x) => Ok(Json.prettyPrint(x))
            case _ => Ok("Error or does not exist")
          }
        }
    )
  }

  def saveGformSchema = Authentication.async(parse.urlFormEncoded) { implicit request =>
    gFormSchema.bindFromRequest().fold(
      formWithErrors => {
        Future.successful(BadRequest(uk.gov.hmrc.eeittadminfrontend.views.html.gform_page(gFormForm, formWithErrors)))
      },
      gformTemplate => {

        //val template = (Json.toJson(request.body)\"template").get
        GformConnector.saveTemplate(gformTemplate).map {
          case Some(x) => Ok(Json.prettyPrint(x))
          case _ => Ok("oops")
        }

  }
    )
  }



  def getAllTemplates = Authentication.async { implicit request =>
    GformConnector.getAllGformsTemplates.map { x =>
      x match {
        case Some(x) => Ok(Json.prettyPrint(x))
        case _ => Ok("Error")
      }
    }
  }

  def getAllSchema = Authentication.async { implicit request =>
    GformConnector.getAllSchema.map { x =>
      x match {
        case Some(x) => Ok(Json.prettyPrint(x))
        case _ => Ok("Error or none exist")
      }
    }
  }


  def gformPage = Authentication.async { implicit request =>
    Future.successful(Ok(uk.gov.hmrc.eeittadminfrontend.views.html.gform_page(gFormForm, gFormSchema)))
  }


  val gFormSchema: Form[GformTemplate] = Form(
    mapping(
      "formTypeId" -> mapping(
        "value" -> text
      )(FormTypeId.apply)(FormTypeId.unapply),
      "version" -> text,
      "template" -> text
    )(GformTemplate.apply)(GformTemplate.unapply)
  )

  val gFormForm: Form[GformIdAndVersion] = Form(
    mapping(
      "formTypeId" -> mapping(
        "value" -> text
      )(FormTypeId.apply)(FormTypeId.unapply),
      "version" -> text
    )(GformIdAndVersion.apply)(GformIdAndVersion.unapply)
  )
}

