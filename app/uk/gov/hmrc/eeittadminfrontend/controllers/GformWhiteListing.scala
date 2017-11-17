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

import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{ I18nSupport, MessagesApi }
import play.api.libs.json.Json
import uk.gov.hmrc.eeittadminfrontend.AppConfig
import uk.gov.hmrc.eeittadminfrontend.config.Authentication
import uk.gov.hmrc.eeittadminfrontend.connectors.GformConnector
import uk.gov.hmrc.play.frontend.auth.Actions
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future

class GformWhiteListing(val authConnector: AuthConnector)(implicit appConfig: AppConfig, val messagesApi: MessagesApi) extends FrontendController with Actions with I18nSupport {

  def showWhitlisting = Authentication.async { implicit request =>
    Future.successful(Ok(uk.gov.hmrc.eeittadminfrontend.views.html.white_listing(request, appConfig)))
  }

  val email = Form(
    single("email" -> nonEmptyText))

  def addUser = Authentication.async { implicit request =>
    email.bindFromRequest().fold(
      hasErrors => Future.successful(Redirect(uk.gov.hmrc.eeittadminfrontend.controllers.routes.GformWhiteListing.showWhitlisting())),
      success => GformConnector.addWhiteListedUser(success).map(_ => Ok("Successful")))
  }

  def deleteUser = Authentication.async { implicit request =>
    email.bindFromRequest().fold(
      hasErrors => Future.successful(Redirect(uk.gov.hmrc.eeittadminfrontend.controllers.routes.GformWhiteListing.showWhitlisting())),
      success => GformConnector.deleteWhiteListedUser(success).map(_ => Ok("Successful")))
  }

  def show = Authentication.async { implicit request =>
    GformConnector.show.map(body => Ok(Json.toJson(body.body)))
  }
}
