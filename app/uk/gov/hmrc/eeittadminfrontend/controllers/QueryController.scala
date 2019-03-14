/*
 * Copyright 2019 HM Revenue & Customs
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
import play.api.i18n.MessagesApi
import play.api.libs.json
import play.api.libs.json._
import play.api.libs.streams.Accumulator
import play.api.mvc.{ Action, BodyParser, Request, RequestHeader }
import uk.gov.hmrc.eeittadminfrontend.AppConfig
import uk.gov.hmrc.eeittadminfrontend.config.Authentication
import uk.gov.hmrc.eeittadminfrontend.connectors.{ ESProxyConnector, EeittConnector, TaxEnrolmentsConnector }
import uk.gov.hmrc.eeittadminfrontend.models._
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future

class QueryController(val authConnector: AuthConnector, val messagesApi: MessagesApi)(implicit appConfig: AppConfig)
    extends FrontendController {

  def getAllBusinessUsers() = Authentication.async { implicit request =>
    Logger.info(s"${request.session.get("token").get} got all etmp business users")
    EeittConnector.getAllBusinessUsers.map(x => Ok(x))
  }

  def getAllAgents() = Authentication.async { implicit request =>
    Logger.info(s"${request.session.get("token").get} got all etmp agents")
    EeittConnector.getAllAgents.map(x => Ok(x))
  }

  def getAllAgentEnrollments() = Authentication.async { implicit request =>
    Logger.info(s"${request.session.get("token").get} got all agent enrollments")
    EeittConnector.getAllAgentEnrollments.map(x => Ok(x))
  }

  def goToQuery = Authentication.async { implicit request =>
    Logger.info(s"${request.session.get("token")} went to Query Page")
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

  def knownFactsQuery() =
    Authentication.async { implicit request =>
      knownFactsForm
        .bindFromRequest()
        .fold(
          formWithErrors => {
            Future.successful(BadRequest(uk.gov.hmrc.eeittadminfrontend.views.html.query_page()))
          },
          getRequest => {
            ESProxyConnector
              .queryKnownFacts(Json.fromJson[List[KnownFact]](Json.parse(getRequest.request)).get)
              .map { y =>
                Ok(y.toString)
              }
          }
        )
        .recover { case e: Exception => BadRequest(e.getMessage) }
    }

  val knownFactsForm: Form[TaxEnrolmentRequest] = Form(
    mapping("identifiers" -> text)(TaxEnrolmentRequest.apply)(TaxEnrolmentRequest.unapply))

  def queryEnrolments = Authentication.async(parse.json[MigrationDataQuery]) { implicit request =>
    val data: MigrationDataQuery = request.body

    TaxEnrolmentsConnector
      .queryEnrolments(data.groupId)
      .map { x =>
        Ok(x.toString)
      }
      .recover { case e: Exception => BadRequest(e.getMessage) }
  }

}

case class MigrationDataQuery(groupId: String)

object MigrationDataQuery {
  implicit val format: Format[MigrationDataQuery] = json.Json.format[MigrationDataQuery]
}

case class TaxEnrolmentRequest(request: String)
