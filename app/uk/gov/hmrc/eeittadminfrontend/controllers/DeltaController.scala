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

import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json._
import uk.gov.hmrc.eeittadminfrontend.AppConfig
import uk.gov.hmrc.eeittadminfrontend.config.Authentication
import uk.gov.hmrc.eeittadminfrontend.connectors.EeittConnector
import uk.gov.hmrc.eeittadminfrontend.models.{ETMPAgent, ETMPBusiness}
import uk.gov.hmrc.play.frontend.auth.Actions
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future

class DeltaController(val authConnector: AuthConnector)(implicit appConfig : AppConfig, val messagesApi: MessagesApi) extends FrontendController with Actions with I18nSupport {

  def getDeltaPage() = Authentication.async {
    Future.successful(Ok("TBM"))
  }

  def agent() = {
    delta[ETMPAgent]
  }

  def business() = {
    delta[ETMPBusiness]
  }

  private def delta[A: Format](implicit eeittConnector: EeittConnector[A]) = Authentication.async(parse.urlFormEncoded) { implicit request =>
      Json.toJson(request.body).validate match {
        case JsSuccess(x, _) =>
          eeittConnector(x)
          Future.successful(Ok(x.toString))
        case JsError(err) =>
          Future.successful(BadRequest(err.toString))
      }
  }

}