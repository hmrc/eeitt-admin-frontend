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

import cats.implicits._
import play.api.i18n.I18nSupport
import play.api.mvc.MessagesControllerComponents
import uk.gov.hmrc.eeittadminfrontend.connectors.GformConnector
import uk.gov.hmrc.eeittadminfrontend.models.{ FormTemplateId, ShutterFormTemplate, ShutterMessageId }
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class ShutterFormTemplateController @Inject() (
  frontendAuthComponents: FrontendAuthComponents,
  gformConnector: GformConnector,
  messagesControllerComponents: MessagesControllerComponents
)(implicit ec: ExecutionContext)
    extends GformAdminFrontendController(frontendAuthComponents, messagesControllerComponents) with I18nSupport {

  def delete(formTemplateId: FormTemplateId) =
    authAction.async { request =>
      gformConnector.deleteShutterFormTemplate(formTemplateId).map { maybeShutter =>
        Redirect(
          uk.gov.hmrc.eeittadminfrontend.controllers.routes.ShutterController.shutter
        )
      }
    }

  def save(shutterMessageId: ShutterMessageId) =
    authAction.async { implicit request =>
      val formTemplateIds =
        request.body.asFormUrlEncoded.map(_.tail.flatMap(_._2).toList).fold(List.empty[FormTemplateId]) {
          formTemplateIds => formTemplateIds.map(FormTemplateId.apply)
        }

      formTemplateIds.traverse { formTemplateId =>
        gformConnector.saveShutterFormTemplates(
          ShutterFormTemplate(formTemplateId, shutterMessageId)
        )
      }
      gformConnector.findShutter().map { _ =>
        Redirect(
          uk.gov.hmrc.eeittadminfrontend.controllers.routes.ShutterController.shutter
        )
      }
    }

}
