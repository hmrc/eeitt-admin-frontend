/*
 * Copyright 2020 HM Revenue & Customs
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
import play.api.i18n.MessagesApi
import play.api.libs.json._
import play.api.mvc.Action
import uk.gov.hmrc.eeittadminfrontend.AppConfig
import uk.gov.hmrc.eeittadminfrontend.config.Authentication
import uk.gov.hmrc.eeittadminfrontend.config.RequestWithUser._
import uk.gov.hmrc.eeittadminfrontend.connectors.{ EeittConnector, EnrolmentStoreProxyConnector }
import uk.gov.hmrc.eeittadminfrontend.models._
import uk.gov.hmrc.http.BadRequestException
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future

class QueryController(val authConnector: AuthConnector, val messagesApi: MessagesApi)(implicit appConfig: AppConfig)
    extends FrontendController {

  def queryEnrolments = Authentication.async { implicit request =>
    val body = request.body.asJson.getOrElse(throw new BadRequestException("bad json"))

    val data: MigrationDataQuery =
      body.asOpt[MigrationDataQuery].getOrElse(throw new BadRequestException("invalid data provided"))

    EnrolmentStoreProxyConnector
      .queryEnrolments(data.groupId)
      .map {
        case Some(data) => Ok(data)
        case None       => NoContent
      }
      .recover { case e: Exception => InternalServerError(e.getMessage) }
  }

  def queryKnownFacts() =
    Authentication.async { implicit request =>
      TaxEnrolmentRequest.knownFactsForm
        .bindFromRequest()
        .fold(
          formWithErrors => {
            Future.successful(BadRequest(uk.gov.hmrc.eeittadminfrontend.views.html.query_page()))
          },
          getRequest => {

            val data = try {
              Json.fromJson[List[KnownFact]](Json.parse(getRequest.request)).get
            } catch {
              case _: Exception => throw new BadRequestException("bad json")
            }

            EnrolmentStoreProxyConnector
              .queryKnownFacts(data)
              .map { y =>
                Ok(y.toString)
              }
          }
        )
        .recover { case e: Exception => InternalServerError(e.getMessage) }
    }

  def getAllBusinessUsers() = Authentication.async { implicit request =>
    Logger.info(s"${request.userLogin} got all etmp business users")
    EeittConnector.getAllBusinessUsers.map(x => Ok(x))
  }

  def getAllAgents() = Authentication.async { implicit request =>
    Logger.info(s"${request.userLogin} got all etmp agents")
    EeittConnector.getAllAgents.map(x => Ok(x))
  }

  def getAllAgentEnrollments() = Authentication.async { implicit request =>
    Logger.info(s"${request.userLogin} got all agent enrollments")
    EeittConnector.getAllAgentEnrollments.map(x => Ok(x))
  }

  def getAllEnrollmentAgents() = Authentication.async { implicit request =>
    Logger.info(s"${request.userLogin} got all enrolmengts agent")
    EeittConnector.getAllEnrolmentsAgents.map(x => Ok(x))
  }

  def getAllEnrollments() = Authentication.async { implicit request =>
    AllEnrolmentRequest.allEnrolmentsForm
      .bindFromRequest()
      .fold(
        formWithErrors => {
          Future.successful(BadRequest(uk.gov.hmrc.eeittadminfrontend.views.html.query_page()))
        },
        getRequest => {
          Logger.info(s"${request.userLogin} got all enrollments for ${getRequest.regimeId}")
          EeittConnector.getAllEnrolments(getRequest.regimeId).map(x => Ok(x))
        }
      )
  }

  def goToQuery = Authentication.async { implicit request =>
    Logger.info(s"${request.userLogin} went to Query Page")
    Future
      .successful(Ok(uk.gov.hmrc.eeittadminfrontend.views.html.query_page())) //uk.gov.hmrc.eeittadminfrontend.views.html.()))
  }

  def goToDelta = Authentication.async { implicit request =>
    Future.successful(Ok(uk.gov.hmrc.eeittadminfrontend.views.html.delta()))
  }

  def UidQuery =
    query[Arn, RegistrationNumber]

  def enrollmentQuery() =
    query[GroupId, Regime]

  private def query[A: Reads, B: Reads]()(implicit connectorA: EeittConnector[A], connectorB: EeittConnector[B]) =
    Authentication.async(parse.urlFormEncoded) { implicit request =>
      val json = Json.toJson(request.body.map(x => x._1 -> x._2.head))
      json.validate[A] match {
        case JsSuccess(x, _) =>
          Logger.debug("x " + x.toString)
          connectorA(x).map { b =>
            Ok(b.toString)
          }
        case JsError(err) =>
          json.validate[B] match {
            case JsSuccess(y, _) =>
              Logger.debug("y " + y.toString)
              connectorB(y).map { b =>
                Ok(b.toString)
              }
            case JsError(error) =>
              Future.successful(Ok("Bad"))
          }
      }
    }
}
