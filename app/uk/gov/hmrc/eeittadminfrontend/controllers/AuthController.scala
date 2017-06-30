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

import cats.data.Validated.{ Invalid, Valid }
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{ I18nSupport, MessagesApi }
import play.api.mvc.{ Action, AnyContent }
import uk.gov.hmrc.eeittadminfrontend.AppConfig
import uk.gov.hmrc.eeittadminfrontend.controllers.auth.{ ClientID, SecuredActions }
import uk.gov.hmrc.eeittadminfrontend.models._
import uk.gov.hmrc.eeittadminfrontend.services.{ AuthService, GoogleVerifier }
import uk.gov.hmrc.play.frontend.auth.Actions
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future

class AuthController(val authConnector: AuthConnector, sa: SecuredActions, authService: AuthService, googleService: GoogleVerifier)(implicit appConfig: AppConfig, val messagesApi: MessagesApi) extends FrontendController with Actions with I18nSupport {

  val clientID: ClientID = pureconfig.loadConfigOrThrow[ClientID]("clientid")

  val loginForm = Form(
    mapping(
      "token" -> nonEmptyText
    )(Token.apply)(Token.unapply)
  )

  def loginPage(): Action[AnyContent] = Action.async { implicit request =>
    sa.whiteListing {
      Future.successful(Ok(uk.gov.hmrc.eeittadminfrontend.views.html.login_page(loginForm, clientID.id)))
    }
  }

  def signOut(): Action[AnyContent] = Action.async { implicit request =>
    Future.successful(Redirect(uk.gov.hmrc.eeittadminfrontend.controllers.routes.AuthController.loginPage()).withNewSession)
  }

  def checkCredentials(): Action[AnyContent] = Action.async { implicit request =>
    loginForm.bindFromRequest.fold(
      error => {
        Logger.error(s"Failed to Login $error")
        Future.successful(BadRequest(uk.gov.hmrc.eeittadminfrontend.views.html.login_page(error, clientID.id)))
      },
      success => {
        val email = Email(googleService(success.value))
        authService.checkUser(email) match {
          case Valid(()) =>
            Future.successful(Redirect(uk.gov.hmrc.eeittadminfrontend.controllers.routes.QueryController.goToQuery()).withSession(request.session + ("token", email.value)))
          case Invalid(err) =>
            Future.successful(Unauthorized(s"Failed ${err.error}"))
        }
      }
    )
  }

}
