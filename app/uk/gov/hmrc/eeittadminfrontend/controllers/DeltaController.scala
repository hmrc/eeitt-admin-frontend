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
import play.api.i18n.{ I18nSupport, MessagesApi }
import play.api.libs.json
import play.api.libs.json._
import play.api.mvc.{ Action, Result }
import uk.gov.hmrc.eeittadminfrontend.AppConfig
import uk.gov.hmrc.eeittadminfrontend.config.Authentication
import uk.gov.hmrc.eeittadminfrontend.connectors.{ EeittConnector, TaxEnrolmentsConnector, UserDetailsConnector }
import uk.gov.hmrc.eeittadminfrontend.models._
import uk.gov.hmrc.play.frontend.auth.Actions
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future

class DeltaController(val authConnector: AuthConnector)(implicit appConfig: AppConfig, val messagesApi: MessagesApi)
    extends FrontendController with Actions with I18nSupport {

  def goToDelta = Authentication.async { implicit request =>
    Logger.info(s"${request.session.get("token")} went to Deltas Page")
    Future.successful(Ok(uk.gov.hmrc.eeittadminfrontend.views.html.delta()))
  }

  def agent() =
    delta[DeltaAgent]

  def business() =
    delta[DeltaBusiness]

  private def delta[A: Format](implicit eeittConnector: EeittConnector[A]) =
    Authentication.async(parse.urlFormEncoded) { implicit request =>
      Logger.info(Json.prettyPrint(Json.toJson(request.body.map(x => x._1 -> x._2.mkString))))
      Json.toJson(request.body.map(x => x._1                              -> x._2.mkString)).validate match {
        case JsSuccess(x, _) =>
          eeittConnector(x).map { y =>
            Ok(y.toString)
          }
        case JsError(err) =>
          Future.successful(BadRequest(err.toString))
      }
    }

  def addFactsEnrol = Authentication.async(parse.json[MigrationData]) { implicit request =>
    val data: MigrationData = request.body

    (for {
      userDetails        <- UserDetailsConnector.userIdbyGroupId(data.groupId)
      es6CreateVerifiers <- TaxEnrolmentsConnector.upsertKnownFacts(data.identifiers, data.verifiers)
      es8AssignEnrolment <- TaxEnrolmentsConnector
                             .addEnrolment(data.groupId, userDetails, data.identifiers, data.verifiers)
    } yield
      (
        Ok
      )).recover { case e: Exception => BadRequest(e.getMessage) }
  }

  def addEnrol = Authentication.async(parse.json[MigrationData]) { implicit request =>
    val data: MigrationData = request.body

    (for {
      userDetails <- UserDetailsConnector.userIdbyGroupId(data.groupId)
      es8AssignEnrolment <- TaxEnrolmentsConnector
                             .addEnrolment(data.groupId, userDetails, data.identifiers, data.verifiers)
    } yield
      (
        Ok
      )).recover { case e: Exception => BadRequest(e.getMessage) }
  }

  def deleteEnrolment = Authentication.async(parse.json[MigrationDataDeallocate]) { implicit request =>
    val data: MigrationDataDeallocate = request.body

    TaxEnrolmentsConnector
      .deallocateEnrolment(data.groupId, data.identifiers)
      .map { x =>
        Ok(x.toString)
      }
      .recover { case e: Exception => BadRequest(e.getMessage) }
  }

  def upsertKnownFacts() =
    Authentication.async { implicit request =>
      upsertKnownFactsRequestForm
        .bindFromRequest()
        .fold(
          formWithErrors => {
            Future.successful(BadRequest(uk.gov.hmrc.eeittadminfrontend.views.html.delta()))
          },
          upsertRequest => {
            upsertRequest.request match {
              case Left(r) =>
                TaxEnrolmentsConnector
                  .upsertKnownFacts(r.identifiers, r.verifiers)
                  .map { y =>
                    Ok(y.body)
                  }
              case Right(e) =>
                Future.successful(BadRequest(e.getMessage))
            }
          }
        )
        .recover { case e: Exception => BadRequest(e.getMessage) }
    }

  def deleteKnownFacts() =
    Authentication.async { implicit request =>
      deleteKnownFactsRequestForm
        .bindFromRequest()
        .fold(
          formWithErrors => {
            Future.successful(BadRequest(uk.gov.hmrc.eeittadminfrontend.views.html.delta()))
          },
          deleteRequest => {
            deleteRequest.request match {
              case Left(r) =>
                TaxEnrolmentsConnector.deleteKnownFacts(r).map { y =>
                  Ok(y.body)
                }
              case Right(e) => Future.successful(BadRequest(e.getMessage))
            }
          }
        )
        .recover { case e: Exception => BadRequest(e.getMessage) }
    }

  val upsertKnownFactsRequestForm: Form[UpsertKnownFactsRequest] = Form(
    mapping("identifiersverifiers" -> text)(UpsertKnownFactsRequest.apply)(UpsertKnownFactsRequest.toStrings))

  val deleteKnownFactsRequestForm: Form[DeleteKnownFactsRequest] = Form(
    mapping("identifiers" -> text)(DeleteKnownFactsRequest.apply)(DeleteKnownFactsRequest.toStrings))

}

case class MigrationData(groupId: String, identifiers: List[Identifier], verifiers: List[Verifier])

object MigrationData {
  implicit val format: Format[MigrationData] = json.Json.format[MigrationData]
}

case class MigrationDataDeallocate(groupId: String, identifiers: List[Identifier])

object MigrationDataDeallocate {
  implicit val format: Format[MigrationDataDeallocate] = json.Json.format[MigrationDataDeallocate]
}

case class UpsertRequest(identifiers: List[Identifier], verifiers: List[Verifier])

case class UpsertKnownFactsRequest(request: Either[UpsertRequest, Exception])

object UpsertKnownFactsRequest {
  def apply(taxEnrolment: String): UpsertKnownFactsRequest =
    try {
      Json
        .fromJson[TaxEnrolment](Json.parse(taxEnrolment))
        .fold(
          invalid => UpsertKnownFactsRequest(Right(new Exception(invalid.toString()))),
          valid => UpsertKnownFactsRequest(Left(UpsertRequest(valid.identifiers, valid.verifiers)))
        )
    } catch {
      case e: Exception => UpsertKnownFactsRequest(Right(e))
    }

  def toStrings(u: UpsertKnownFactsRequest): Option[String] = u.request match {
    case Left(x)  => Some(Json.toJson(TaxEnrolment(x.identifiers, x.verifiers)).toString())
    case Right(e) => None
  }
}

case class DeleteKnownFactsRequest(request: Either[List[Identifier], Exception])

object DeleteKnownFactsRequest {
  def apply(identifiers: String): DeleteKnownFactsRequest =
    try {
      Json
        .fromJson[List[Identifier]](Json.parse(identifiers))
        .fold(
          invalid => DeleteKnownFactsRequest(Right(new Exception(invalid.toString()))),
          valid => DeleteKnownFactsRequest(Left(valid))
        )
    } catch {
      case e: Exception => DeleteKnownFactsRequest(Right(e))
    }
  def toStrings(d: DeleteKnownFactsRequest): Option[String] = d.request match {
    case Left(x)  => Some(Json.toJson(x).toString())
    case Right(e) => None
  }
}
