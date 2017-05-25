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

import java.util.Collections

import cats.data.Validated.{Invalid, Valid}
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.eeittadminfrontend.AppConfig
import uk.gov.hmrc.eeittadminfrontend.config.Authentication
import uk.gov.hmrc.eeittadminfrontend.connectors.EeittAdminConnector
import uk.gov.hmrc.eeittadminfrontend.controllers.auth.SecuredActions
import uk.gov.hmrc.eeittadminfrontend.models._
import uk.gov.hmrc.eeittadminfrontend.services.GoogleVerifier
import uk.gov.hmrc.play.frontend.auth.Actions
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future

class AuthController(val authConnector: AuthConnector,eeittAdminConnector: EeittAdminConnector, sa : SecuredActions, googleService : GoogleVerifier)(implicit appConfig : AppConfig, val messagesApi: MessagesApi) extends FrontendController with Actions with I18nSupport{

  val loginForm = Form(
    mapping(
      "token" -> nonEmptyText
    )(Token.apply)(Token.unapply)
  )

  val deleteForm = Form(
    mapping(
      "email" -> nonEmptyText
    )(Email.apply)(Email.unapply)
  )


  val registerForm = Form(
    mapping(
        "email" -> mapping(
          "value" -> nonEmptyText
        )(Email.apply)(Email.unapply),
        "permissions" -> seq[Permission](mapping(
          "value" -> nonEmptyText
      )(Permission.apply)(Permission.unapply))
    )(User.apply)(User.unapply))

  def loginPage() = Action.async { implicit request =>
    sa.whiteListing {
      Future.successful(Ok(uk.gov.hmrc.eeittadminfrontend.views.html.login_page(loginForm)))
    }
  }

  def getRegistration = Authentication.async { implicit request =>
    Future.successful(Ok(uk.gov.hmrc.eeittadminfrontend.views.html.registration(registerForm)))
  }

  def register: Action[AnyContent] = Action.async { implicit request =>
    Logger.debug(request.body.toString)
    registerForm.bindFromRequest.fold(
      error =>{
        Future.successful(Ok(error.errors.toString))
      },
      success => {
        Logger.debug(request.body.toString)
        eeittAdminConnector.register(success).map {
          case Valid(x) => Ok("Worked")
          case Invalid(y) => Ok("Failed")
        }
      }
    )
  }

  def checkCredentials(): Action[AnyContent] = Action.async { implicit request =>
    loginForm.bindFromRequest.fold(
      error => {
        Logger.error(s"Failed to Login $error")
        Future.successful(BadRequest(uk.gov.hmrc.eeittadminfrontend.views.html.login_page(error)))
      },
      success => {
        val token = googleService(success.value)
        val email = Email(token)
        eeittAdminConnector.checkAuth(email).map{
          case Valid(x) =>
            Redirect(uk.gov.hmrc.eeittadminfrontend.controllers.routes.QueryController.goToQuery()).withSession(request.session + ("token", x.email.value))
          case Invalid(err) =>
            Unauthorized(s"Failed ${err.error}")
        }
      }
    )
  }

}
