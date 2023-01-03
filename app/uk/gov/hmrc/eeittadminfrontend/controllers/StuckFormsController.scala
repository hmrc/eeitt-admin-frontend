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

package uk.gov.hmrc.eeittadminfrontend.controllers

import javax.inject.Inject
import org.slf4j.{ Logger, LoggerFactory }
import play.api.i18n.I18nSupport
import play.api.mvc.MessagesControllerComponents
import uk.gov.hmrc.eeittadminfrontend.connectors.GformConnector
import uk.gov.hmrc.eeittadminfrontend.models.FormId
import uk.gov.hmrc.eeittadminfrontend.models.fileupload.EnvelopeId
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents

import scala.concurrent.ExecutionContext

class StuckFormsController @Inject() (
  frontendAuthComponents: FrontendAuthComponents,
  gformConnector: GformConnector,
  messagesControllerComponents: MessagesControllerComponents,
  stuck_forms: uk.gov.hmrc.eeittadminfrontend.views.html.stuck_forms
)(implicit ec: ExecutionContext)
    extends GformAdminFrontendController(frontendAuthComponents, messagesControllerComponents) with I18nSupport {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  def stuckForms() =
    authAction.async { implicit request =>
      gformConnector.getSignedFormsDetails.map { signedFormDetails =>
        Ok(stuck_forms(signedFormDetails))
      }
    }

  def releaseForm(formId: FormId, envelopeId: EnvelopeId) =
    authAction.async { implicit request =>
      val username = request.retrieval
      logger.info(s"$username changing formId: ${formId.value}, envelopeId: ${envelopeId.value} status to InProgress")
      gformConnector.unstuckForm(formId).map { httpResponse =>
        Redirect(routes.StuckFormsController.stuckForms())
          .flashing("success" -> s"Form successfuly unstuck. EnvelopeId: ${envelopeId.value}")
      }
    }

  def deleteForm(formId: FormId, envelopeId: EnvelopeId) =
    authAction.async { implicit request =>
      val username = request.retrieval
      logger.info(s"$username deleting formId: ${formId.value}, envelopeId: ${envelopeId.value}")
      gformConnector.deleteForm(formId).map { httpResponse =>
        Redirect(routes.StuckFormsController.stuckForms())
          .flashing("success" -> s"Form successful deleted. EnvelopeId: ${envelopeId.value}")
      }
    }
}
