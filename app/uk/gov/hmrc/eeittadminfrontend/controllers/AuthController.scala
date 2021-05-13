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

import cats.data.Validated.{ Invalid, Valid }
import org.slf4j.{ Logger, LoggerFactory }
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.I18nSupport
import play.api.mvc.{ Action, AnyContent, MessagesControllerComponents }
import pureconfig.ConfigSource
import uk.gov.hmrc.eeittadminfrontend.auth.AuthConnector
import uk.gov.hmrc.eeittadminfrontend.controllers.auth.{ ClientID, SecuredActions }
import uk.gov.hmrc.eeittadminfrontend.models._
import uk.gov.hmrc.eeittadminfrontend.services.AuthService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import pureconfig.generic.auto._
import uk.gov.hmrc.eeittadminfrontend.config.AppConfig

import scala.concurrent.Future

class AuthController(
  val authConnector: AuthConnector,
  sa: SecuredActions,
  authService: AuthService,
  messagesControllerComponents: MessagesControllerComponents
)(implicit appConfig: AppConfig)
    extends FrontendController(messagesControllerComponents) with I18nSupport {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  val clientID: ClientID = ConfigSource.default.at("clientid").loadOrThrow[ClientID]

  val loginForm: play.api.data.Form[UserData] =
    Form(
      mapping(
        "username" -> nonEmptyText,
        "email"    -> nonEmptyText
      )((username, email) => UserData(Username(username), Email(email)))(userData =>
        Some((userData.username.value, userData.email.value))
      )
    )

  def loginPage(): Action[AnyContent] =
    Action.async { implicit request =>
      sa.whiteListing {
        Future.successful(Ok(uk.gov.hmrc.eeittadminfrontend.views.html.login_page(loginForm, clientID.id)))
      }
    }

  def signOut(): Action[AnyContent] =
    Action.async { _ =>
      Future.successful(
        Redirect(uk.gov.hmrc.eeittadminfrontend.controllers.routes.AuthController.loginPage()).withNewSession
      )
    }

  def checkCredentials(): Action[AnyContent] =
    Action.async { implicit request =>
      loginForm.bindFromRequest.fold(
        error => {
          logger.error(s"Failed to Login $error")
          Future.successful(BadRequest(uk.gov.hmrc.eeittadminfrontend.views.html.login_page(error, clientID.id)))
        },
        userData => {
          val email = userData.email
          authService.checkUser(email) match {
            case Valid(()) =>
              logger.info(s"$userData Logged in")
              Future.successful(
                Redirect(uk.gov.hmrc.eeittadminfrontend.controllers.routes.GformsController.gformPage)
                  .addingToSession("email" -> email.value, "username" -> userData.username.value)
              )
            case Invalid(err) =>
              Future.successful(Unauthorized(s"Failed ${err.error}"))
          }
        }
      )
    }

}
