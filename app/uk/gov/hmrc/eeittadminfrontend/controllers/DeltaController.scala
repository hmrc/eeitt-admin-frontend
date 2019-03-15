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
import play.api.i18n.{ I18nSupport, MessagesApi }
import play.api.libs.json._
import uk.gov.hmrc.eeittadminfrontend.AppConfig
import uk.gov.hmrc.eeittadminfrontend.config.Authentication
import uk.gov.hmrc.eeittadminfrontend.connectors.{ EeittConnector, EnrolmentStoreProxyConnector, UserDetailsConnector }
import uk.gov.hmrc.eeittadminfrontend.models._
import uk.gov.hmrc.http.BadRequestException
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

  def addFactsEnrol = Authentication.async { implicit request =>
    val body = request.body.asJson.getOrElse(throw new BadRequestException("bad json"))

    val data: MigrationData =
      body.asOpt[MigrationData].getOrElse(throw new BadRequestException("invalid data provided"))

    (for {
      userDetails        <- UserDetailsConnector.userIdbyGroupId(data.groupId)
      es6CreateVerifiers <- EnrolmentStoreProxyConnector.upsertKnownFacts(data.identifiers, data.verifiers)
      es8AssignEnrolment <- EnrolmentStoreProxyConnector
                             .addEnrolment(data.groupId, userDetails, data.identifiers, data.verifiers)
    } yield Ok).recover {
      case e: Exception => InternalServerError(e.getMessage)
    }
  }

  def addEnrol = Authentication.async { implicit request =>
    val body = request.body.asJson.getOrElse(throw new BadRequestException("bad json"))

    val data: MigrationData =
      body.asOpt[MigrationData].getOrElse(throw new BadRequestException("invalid data provided"))

    (for {
      userDetails <- UserDetailsConnector.userIdbyGroupId(data.groupId)
      es8AssignEnrolment <- EnrolmentStoreProxyConnector
                             .addEnrolment(data.groupId, userDetails, data.identifiers, data.verifiers)
    } yield (Ok)).recover { case e: Exception => InternalServerError(e.getMessage) }
  }

  def deleteEnrolment = Authentication.async { implicit request =>
    val body = request.body.asJson.getOrElse(throw new BadRequestException("bad json"))

    val data: MigrationDataDeallocate =
      body.asOpt[MigrationDataDeallocate].getOrElse(throw new BadRequestException("invalid data provided"))

    EnrolmentStoreProxyConnector
      .deallocateEnrolment(data.groupId, data.identifiers)
      .map { x =>
        Ok(x.toString)
      }
      .recover { case e: Exception => InternalServerError(e.getMessage) }
  }

  def deleteKnownFacts() = Authentication.async { implicit request =>
    DeleteKnownFactsRequest.deleteKnownFactsRequestForm
      .bindFromRequest()
      .fold(
        formWithErrors => Future.successful(BadRequest(uk.gov.hmrc.eeittadminfrontend.views.html.delta())),
        deleteRequest => {
          deleteRequest.request match {
            case Left(r) =>
              EnrolmentStoreProxyConnector.deleteKnownFacts(r).map { response =>
                Ok(response.body)
              }
            case Right(e) => Future.successful(BadRequest(e.getMessage))
          }
        }
      )
      .recover { case e: Exception => InternalServerError(e.getMessage) }
  }

  def upsertKnownFacts() =
    Authentication.async { implicit request =>
      UpsertKnownFactsRequest.upsertKnownFactsRequestForm
        .bindFromRequest()
        .fold(
          formWithErrors => {
            Future.successful(BadRequest(uk.gov.hmrc.eeittadminfrontend.views.html.delta()))
          },
          upsertRequest => {
            upsertRequest.request match {
              case Left(upsertReq) =>
                EnrolmentStoreProxyConnector
                  .upsertKnownFacts(upsertReq.identifiers, upsertReq.verifiers)
                  .map { response =>
                    Ok(response.body)
                  }
              case Right(e) =>
                Future.successful(BadRequest(e.getMessage))
            }
          }
        )
        .recover { case e: Exception => InternalServerError(e.getMessage) }
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
}
