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

package uk.gov.hmrc.eeittadminfrontend
package controllers

import play.api.Logger
import play.api.data.Form
import play.api.data.Forms.{ mapping, text }
import play.api.i18n.I18nSupport
import play.api.libs.json.Json
import play.api.mvc.{ MessagesControllerComponents, Result }
import uk.gov.hmrc.eeittadminfrontend.auth.AuthConnector
import uk.gov.hmrc.eeittadminfrontend.config.{ AppConfig, AuthAction }
import uk.gov.hmrc.eeittadminfrontend.connectors.FileUploadConnector
import uk.gov.hmrc.eeittadminfrontend.models.fileupload.{ EnvelopeId, EnvelopeIdForm }
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

import scala.concurrent.{ ExecutionContext, Future }

class FileUploadController(
  val authConnector: AuthConnector,
  authAction: AuthAction,
  fileUploadConnector: FileUploadConnector,
  messagesControllerComponents: MessagesControllerComponents)(implicit ec: ExecutionContext, appConfig: AppConfig)
    extends FrontendController(messagesControllerComponents) with I18nSupport {

  private val envelopeIdForm: Form[EnvelopeIdForm] = Form(
    mapping("envelopeId" -> mapping("value" -> text)(EnvelopeId.apply)(EnvelopeId.unapply))(EnvelopeIdForm.apply)(
      EnvelopeIdForm.unapply)
  )

  def fileUpload() = authAction.async { implicit request =>
    Future.successful(Ok(views.html.file_upload(envelopeIdForm)))
  }

  private def WithUserLogin(f: (EnvelopeId, String) => HeaderCarrier => Future[Result]) =
    authAction.async { implicit request =>
      envelopeIdForm
        .bindFromRequest()
        .fold(
          formWithErrors => {
            Future.successful(BadRequest(uk.gov.hmrc.eeittadminfrontend.views.html.file_upload(envelopeIdForm)))
          },
          envelopeId => f(envelopeId.envelopeId, request.userLogin)(hc)
        )
    }

  def findEnvelope() = WithUserLogin { (envelopeId, userLogin) => implicit hc =>
    Logger.info(s"$userLogin Queried for envelopeId $envelopeId")
    fileUploadConnector.getEnvelopeById(envelopeId).map {
      case Right(payload) => Ok(Json.prettyPrint(payload))
      case Left(error)    => BadRequest(error)
    }
  }

  def showEnvelope(envelopeId: EnvelopeId) = authAction.async { implicit request =>
    fileUploadConnector.getEnvelopeById(envelopeId).map {
      case Right(payload) => Ok(Json.prettyPrint(payload))
      case Left(error)    => BadRequest(error)
    }
  }

  def downloadEnvelope(envelopeId: EnvelopeId) = authAction.async { implicit request =>
    Logger.info(s"${request.userLogin} Download an envelopeId $envelopeId")
    fileUploadConnector.downloadEnvelopeId(envelopeId).map {
      case Right(source) =>
        Ok.chunked(source)
          .withHeaders(
            CONTENT_TYPE        -> "application/zip",
            CONTENT_DISPOSITION -> s"""attachment; filename = "${envelopeId.value}.zip""""
          )
      case Left(error) => BadRequest(error)
    }
  }

  def downloadEnvelopePost() = WithUserLogin { (envelopeId, userLogin) => _ =>
    Future.successful(
      Redirect(uk.gov.hmrc.eeittadminfrontend.controllers.routes.FileUploadController.downloadEnvelope(envelopeId)))
  }

  def archiveEnvelope() = WithUserLogin { (envelopeId, userLogin) => implicit hc =>
    Logger.info(s"$userLogin Delete envelopeId $envelopeId")
    fileUploadConnector.archiveEnvelopeId(envelopeId).map {
      case Right(payload) => Ok(payload)
      case Left(error)    => BadRequest(error)
    }
  }
}
