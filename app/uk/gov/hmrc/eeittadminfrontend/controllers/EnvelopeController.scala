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

import org.slf4j.{ Logger, LoggerFactory }
import play.api.data.Form
import play.api.data.Forms.{ mapping, nonEmptyText, text }
import play.api.i18n.I18nSupport
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ Action, AnyContent, MessagesControllerComponents, Result }
import uk.gov.hmrc.eeittadminfrontend.connectors.GformConnector
import uk.gov.hmrc.eeittadminfrontend.models.fileupload.{ EnvelopeId, EnvelopeIdForm }
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.internalauth.client.{ AuthenticatedRequest, FrontendAuthComponents, Retrieval }

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

case class AccessEnvelopeForm(accessReason: String, envelopeId: String)

class EnvelopeController @Inject() (
  frontendAuthComponents: FrontendAuthComponents,
  gformConnector: GformConnector,
  messagesControllerComponents: MessagesControllerComponents,
  envelope_html: views.html.envelope,
  envelope_options: views.html.envelope_options
)(implicit ec: ExecutionContext)
    extends GformAdminFrontendController(frontendAuthComponents, messagesControllerComponents) with I18nSupport {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  private val envelopeIdForm: Form[EnvelopeIdForm] = Form(
    mapping("envelopeId" -> mapping("value" -> text)(EnvelopeId.apply)(EnvelopeId.unapply))(EnvelopeIdForm.apply)(
      EnvelopeIdForm.unapply
    )
  )

  private val accessEnvelopeForm: Form[AccessEnvelopeForm] = Form(
    mapping(
      "accessReason" -> nonEmptyText,
      "envelopeId"   -> nonEmptyText
    )(AccessEnvelopeForm.apply)(AccessEnvelopeForm.unapply)
  )

  def envelope() =
    authorizedRead.async { implicit request =>
      Future.successful(Ok(envelope_html(envelopeIdForm)))
    }

  private def WithUserLogin(f: (EnvelopeId, String) => HeaderCarrier => Future[Result]) =
    authorizedRead.async { implicit request =>
      envelopeIdForm
        .bindFromRequest()
        .fold(
          formWithErrors => Future.successful(BadRequest(envelope_html(envelopeIdForm))),
          envelopeId => f(envelopeId.envelopeId, request.retrieval.value)(hc)
        )
    }

  def findEnvelope() =
    WithUserLogin { (envelopeId, userData) => implicit hc =>
      logger.info(s"User '$userData' queried for gform envelopeId $envelopeId")
      gformConnector.getEnvelopeById(envelopeId).map {
        case Right(_)    => Redirect(routes.EnvelopeController.envelopeOptions(envelopeId))
        case Left(error) => BadRequest(error)
      }
    }

  def envelopeOptions(envelopeId: EnvelopeId) =
    authorizedRead.async { implicit request =>
      Future.successful(Ok(envelope_options(envelopeId)))
    }

  def showEnvelope(envelopeId: EnvelopeId): Action[AnyContent] =
    authorizedRead.async { implicit request =>
      showJsonResult(
        gformConnector.getEnvelopeById(envelopeId),
        s"User '$username' viewed gform envelopeId '$envelopeId'"
      )
    }

  def showRetrievals(): Action[AnyContent] =
    authorizedRead.async { implicit request =>
      accessEnvelopeForm
        .bindFromRequest()
        .fold(
          formWithErrors =>
            Future.successful {
              formWithErrors.data.get("envelopeId").fold(BadRequest(envelope_html(envelopeIdForm))) { envelopeId =>
                BadRequest(envelope_options(EnvelopeId(envelopeId)))
              }
            },
          accessEnvelope =>
            showJsonResult(
              gformConnector.getRetrievalsForEnvelopeId(EnvelopeId(accessEnvelope.envelopeId)),
              s"User '$username', reason '${accessEnvelope.accessReason}', viewed authenticated user retrievals for gform envelopeId '${accessEnvelope.envelopeId}'"
            )
        )
    }

  def showFormData(): Action[AnyContent] =
    authorizedRead.async { implicit request =>
      accessEnvelopeForm
        .bindFromRequest()
        .fold(
          formWithErrors =>
            Future.successful {
              formWithErrors.data.get("envelopeId").fold(BadRequest(envelope_html(envelopeIdForm))) { envelopeId =>
                BadRequest(envelope_options(EnvelopeId(envelopeId)))
              }
            },
          accessEnvelope =>
            showJsonResult(
              gformConnector.getFormDataForEnvelopeId(EnvelopeId(accessEnvelope.envelopeId)),
              s"User '$username', reason '${accessEnvelope.accessReason}', viewed form data for gform envelopeId '${accessEnvelope.envelopeId}'"
            )
        )
    }

  private def username(implicit request: AuthenticatedRequest[AnyContent, Retrieval.Username]): String =
    request.retrieval.value

  private def showJsonResult(f: Future[Either[String, JsValue]], message: String): Future[Result] =
    f.map {
      case Right(payload) =>
        logger.info(message)
        Ok(Json.prettyPrint(payload))
      case Left(error) => BadRequest(error)
    }
}
