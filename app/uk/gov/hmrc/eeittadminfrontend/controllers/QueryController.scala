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

import play.api.i18n.MessagesApi
import play.api.libs.json._
import play.api.mvc.Action
import uk.gov.hmrc.eeittadminfrontend.AppConfig
import uk.gov.hmrc.eeittadminfrontend.connectors.EeittConnector
import uk.gov.hmrc.eeittadminfrontend.models.{GroupId, Regime, RegistrationNumber}
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future

class QueryController(val authConnector: AuthConnector, val messagesApi: MessagesApi)(implicit appConfig : AppConfig) extends FrontendController {

  def goToQuery = Action.async { implicit request =>
    Future.successful(Ok(uk.gov.hmrc.eeittadminfrontend.views.html.query_page()))//uk.gov.hmrc.eeittadminfrontend.views.html.()))
  }

  def registrationQuery() = {
    query[RegistrationNumber]()
  }

  def groupIdQuery() = {
    query[GroupId]()
  }

  def regimeQuery() = {
    query[Regime]()
  }

  private def query[A:  Reads]()(implicit connector : EeittConnector[A]) = Action.async(parse.urlFormEncoded) { implicit request =>
    val search = Json.toJson(request.body).validate match {
      case JsSuccess(x, _) => connector(x)
      case JsError(err) =>
    }
    Future.successful(Ok(search.toString))
  }
}