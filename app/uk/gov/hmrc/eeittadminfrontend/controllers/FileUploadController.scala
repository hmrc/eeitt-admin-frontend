/*
 * Copyright 2023 HM Revenue & Customs
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

import javax.inject.Inject
import org.slf4j.{ Logger, LoggerFactory }
import play.api.data.Form
import play.api.data.Forms.{ mapping, text }
import play.api.i18n.I18nSupport
import play.api.libs.json.Json
import play.api.mvc.{ MessagesControllerComponents, Result }
import uk.gov.hmrc.eeittadminfrontend.connectors.FileUploadConnector
import uk.gov.hmrc.eeittadminfrontend.models.fileupload.{ EnvelopeId, EnvelopeIdForm }
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.internalauth.client.{ FrontendAuthComponents, Retrieval }

import scala.concurrent.{ ExecutionContext, Future }

class FileUploadController @Inject() (
  frontendAuthComponents: FrontendAuthComponents,
  fileUploadConnector: FileUploadConnector,
  messagesControllerComponents: MessagesControllerComponents,
  file_upload: views.html.file_upload
)(implicit ec: ExecutionContext)
    extends GformAdminFrontendController(frontendAuthComponents, messagesControllerComponents) with I18nSupport {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  private val envelopeIdForm: Form[EnvelopeIdForm] = Form(
    mapping("envelopeId" -> mapping("value" -> text)(EnvelopeId.apply)(EnvelopeId.unapply))(EnvelopeIdForm.apply)(
      EnvelopeIdForm.unapply
    )
  )

  def fileUpload() =
    authorizedWrite.async { implicit request =>
      Future.successful(Ok(file_upload(envelopeIdForm)))
    }

  private def WithUserLogin(f: (EnvelopeId, Retrieval.Username) => HeaderCarrier => Future[Result]) =
    authorizedRead.async { implicit request =>
      envelopeIdForm
        .bindFromRequest()
        .fold(
          formWithErrors => Future.successful(BadRequest(file_upload(envelopeIdForm))),
          envelopeId => f(envelopeId.envelopeId, request.retrieval)(hc)
        )
    }

  def findEnvelope() =
    WithUserLogin { (envelopeId, userData) => hc =>
      logger.info(s"$userData Queried for envelopeId $envelopeId")
      fileUploadConnector.getEnvelopeById(envelopeId).map {
        case Right(payload) => Ok(Json.prettyPrint(payload))
        case Left(error)    => BadRequest(error)
      }
    }

  def showEnvelope(envelopeId: EnvelopeId) =
    authorizedRead.async { request =>
      fileUploadConnector.getEnvelopeById(envelopeId).map {
        case Right(payload) => Ok(Json.prettyPrint(payload))
        case Left(error)    => BadRequest(error)
      }
    }

  def downloadEnvelope(envelopeId: EnvelopeId) =
    authorizedRead.async { implicit request =>
      val username = request.retrieval.value
      logger.info(s"$username Download an envelopeId $envelopeId")
      fileUploadConnector.downloadEnvelopeId(envelopeId).map {
        case Right(source) =>
          Ok.streamed(source, None)
            .withHeaders(
              CONTENT_TYPE        -> "application/zip",
              CONTENT_DISPOSITION -> s"""attachment; filename = "${envelopeId.value}.zip""""
            )
        case Left(error) => BadRequest(error)
      }
    }

  def downloadEnvelopePost() =
    WithUserLogin { (envelopeId, userData) => _ =>
      Future.successful(
        Redirect(uk.gov.hmrc.eeittadminfrontend.controllers.routes.FileUploadController.downloadEnvelope(envelopeId))
      )
    }

  def archiveEnvelope() =
    WithUserLogin { (envelopeId, userData) => implicit hc =>
      logger.info(s"$userData Delete envelopeId $envelopeId")
      fileUploadConnector.archiveEnvelopeId(envelopeId).map {
        case Right(payload) => Ok(payload)
        case Left(error)    => BadRequest(error)
      }
    }
}
