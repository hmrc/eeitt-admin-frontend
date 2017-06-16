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
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.eeittadminfrontend.AppConfig
import uk.gov.hmrc.eeittadminfrontend.config.Authentication
import uk.gov.hmrc.eeittadminfrontend.connectors._
import uk.gov.hmrc.play.frontend.auth.Actions
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future

case class User(credId: String, userId: String, groupId: String)

class BulkGGLoad(val authConnector: AuthConnector, eMACConnector: EMACConnector)(implicit val messagesApi: MessagesApi, appConfig: AppConfig) extends FrontendController with Actions with I18nSupport {

  def getBulkloadPage = Authentication.async{ implicit request =>
    Future.successful(Ok(uk.gov.hmrc.eeittadminfrontend.views.html.gg_bulkload(knownFactsForm, allocateEnrolment)))

  }

  private val switch: Boolean = pureconfig.loadConfigOrThrow[Boolean]("feature.switch.GGLoad.value")

  val listOfCredIds: Map[String, User] = if(switch){
    Map("USER1" -> pureconfig.loadConfigOrThrow[User]("feature.GGLoad.users.user1"), "USER2" -> pureconfig.loadConfigOrThrow[User]("feature.GGLoad.users.user2"), "USER3" -> pureconfig.loadConfigOrThrow[User]("feature.GGLoad.users.user3"))
  } else Map.empty[String, User]

  val knownFactsForm = Form(
    mapping(
      "enrollmentKey" -> mapping(
        "service" -> nonEmptyText,
        "identifier" -> nonEmptyText,
        "value" -> nonEmptyText
      )(EnrollmentKey.apply)(EnrollmentKey.unapply),
        "verifiers" -> list(mapping(
          "key" -> nonEmptyText,
          "value" -> nonEmptyText
        )(KeyValuePair.apply)(KeyValuePair.unapply))
    )(KnownFacts.apply)(KnownFacts.unapply)
  )

  val allocateEnrolment = Form(
    mapping(
      "user" -> nonEmptyText,
      "enrolmentKey" -> mapping(
        "service" -> nonEmptyText,
        "identifier" -> nonEmptyText,
        "value" -> nonEmptyText
      )(EnrollmentKey.apply)(EnrollmentKey.unapply),
        "verifier" -> list(mapping(
          "key" -> nonEmptyText,
          "value" -> nonEmptyText
        )(KeyValuePair.apply)(KeyValuePair.unapply))
    )(Enrollment.apply)(Enrollment.unapply)
  )

  def loadKF(): Action[AnyContent] = Authentication.async { implicit request =>
    if(switch) {
      knownFactsForm.bindFromRequest.fold(
        errors =>
          Future.successful(BadRequest("Failed")),
        success => {
          eMACConnector.loadKF(success).map {
            case None =>
              Ok("Success")
            case Some(x) =>
              Ok("Failed" + x.toString)
          }
        }
      )
    } else Future.successful(BadRequest("Feature Invalid"))
  }

  def assignEnrollment(): Action[AnyContent] = Authentication.async { implicit request =>
    allocateEnrolment.bindFromRequest.fold(
      errors =>
        Future.successful(BadRequest("Failed")),
      success => {
        val user = listOfCredIds(success.user)
        eMACConnector.assignEnrollment(success, user).map{
          case None =>
            Ok("Successful!!!")
          case Some(x) =>
            Ok(x.toString)
        }
      }
    )
  }
}
