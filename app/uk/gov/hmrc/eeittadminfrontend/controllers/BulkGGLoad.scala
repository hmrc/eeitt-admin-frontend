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
import play.api.mvc.{ Action, AnyContent }
import uk.gov.hmrc.eeittadminfrontend.AppConfig
import uk.gov.hmrc.eeittadminfrontend.config.Authentication
import uk.gov.hmrc.eeittadminfrontend.connectors.EeittConnector.sc
import uk.gov.hmrc.eeittadminfrontend.connectors._
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.frontend.auth.Actions
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.controller.FrontendController
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

case class Switch(value: Boolean)
case class User(credId: String, userId: String, groupId: String)

class BulkGGLoad(val authConnector: AuthConnector, eMACConnector: EMACConnector)(implicit val messagesApi: MessagesApi, appConfig: AppConfig) extends FrontendController with Actions with I18nSupport {

  def getBulkloadPage = Authentication.async { implicit request =>

    Future.successful(Ok(uk.gov.hmrc.eeittadminfrontend.views.html.gg_bulkload(knownFactsForm, allocateEnrolment, listOfCredIds, "")))

  }

  private val switch: Boolean = pureconfig.loadConfigOrThrow[Switch]("feature.GGLoad.switch").value
  //  private lazy val sc = new ServicesConfig {}
  //  lazy val adminFrontend: String = s"${sc.baseUrl("eeitt-admin-frontend")}/eeitt-admin-frontend/echo"
  val listOfCredIds: Map[String, User] = if (switch) {
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
      "verifiers" -> list(mapping(
        "key" -> nonEmptyText,
        "value" -> nonEmptyText
      )(KeyValuePair.apply)(KeyValuePair.unapply)),
      "friendlyName" -> nonEmptyText
    )(Enrollment.apply)(Enrollment.unapply)
  )

  val deleteForm = Form(
    mapping(
      "user" -> nonEmptyText,
      "enrollmentKey" -> mapping(
        "service" -> nonEmptyText,
        "identifier" -> nonEmptyText,
        "value" -> nonEmptyText
      )(EnrollmentKey.apply)(EnrollmentKey.unapply)
    )(Delete.apply)(Delete.unapply)
  )

  /*  def loadKF(): Action[AnyContent] = Authentication.async { implicit request =>
    Logger.debug("ERROR ::: " + request.body.toString)
    if (switch) {
      knownFactsForm.bindFromRequest.fold(
        errors =>
          Future.successful(BadRequest("Failed")),
        success => {
          Logger.debug(success.verifiers.toString + "///////////////////////////")
          eMACConnector.loadKF(success).map {
            case None =>
              Ok("Success")
            case Some(x) =>
              Ok("Failed" + x.toString)
          }
        }
      )
    } else Future.successful(BadRequest("Feature Invalid"))
  }*/

  def assignEnrollment(): Action[AnyContent] = Authentication.async { implicit request =>
    allocateEnrolment.bindFromRequest.fold(
      errors =>
        Future.successful(BadRequest("Failed")),
      success => {
        val user = listOfCredIds(success.user)
        eMACConnector.assignEnrollment(success, user).map {
          case None =>
            Ok("Successful!!!")
          case Some(x) =>
            Ok(x.toString)
        }
      }
    )
  }

  def deleteEndPoint(): Action[AnyContent] = Authentication.async { implicit request =>
    deleteForm.bindFromRequest.fold(
      errors =>
        Future.successful(BadRequest("Ok")),
      success => {
        val user = listOfCredIds(success.user)
        for {
          result <- startDelete(success.enrollmentKey, user)
        } yield {
          if (result) {
            Ok("DELETED")
          } else BadRequest("Failed Check logs")
        }
      }
    )
  }

  def startDelete(enrollmentKey: EnrollmentKey, user: User)(implicit hc: HeaderCarrier): Future[Boolean] = {
    eMACConnector.deassignEnrollment(enrollmentKey, user).flatMap {
      case None =>
        deallocate(enrollmentKey, user)
      case Some(x) =>
        Logger.error(Json.prettyPrint(x))
        Future.successful(false)
    }
  }

  def deallocate(enrollmentKey: EnrollmentKey, user: User)(implicit hc: HeaderCarrier): Future[Boolean] = {
    eMACConnector.deallocateEnrollment(enrollmentKey, user).flatMap {
      case None =>
        removeKf(enrollmentKey)
      case Some(x) =>
        Logger.error(Json.prettyPrint(x))
        Future.successful(false)
    }
  }

  def removeKf(enrollmentKey: EnrollmentKey)(implicit hc: HeaderCarrier): Future[Boolean] = {
    eMACConnector.removeUnallocated(enrollmentKey).map {
      case None =>
        true
      case Some(x) =>
        Logger.error(Json.prettyPrint(x))
        false
    }
  }
}
