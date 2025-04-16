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
import uk.gov.hmrc.eeittadminfrontend.models.{ BannerId, FormTemplateId, GformNotificationBannerFormTemplate }
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class NotificationBannerFormTemplateController @Inject() (
  frontendAuthComponents: FrontendAuthComponents,
  gformConnector: GformConnector,
  messagesControllerComponents: MessagesControllerComponents
)(implicit ec: ExecutionContext)
    extends GformAdminFrontendController(frontendAuthComponents, messagesControllerComponents) with I18nSupport {

  def delete(formTemplateId: FormTemplateId) =
    authorizedDelete.async { implicit request =>
      gformConnector.deleteNotificationBannerFormTemplate(formTemplateId).map { maybeNotificationBanner =>
        Redirect(
          uk.gov.hmrc.eeittadminfrontend.controllers.routes.NotificationBannerController.notificationBanner
        )
      }
    }

  def save(bannerId: BannerId) =
    authorizedWrite.async { implicit request =>
      val formTemplateIds =
        request.body.asFormUrlEncoded.map(_.tail.flatMap(_._2).toList).fold(List.empty[FormTemplateId]) {
          formTemplateIds => formTemplateIds.map(FormTemplateId.apply)
        }

      formTemplateIds.traverse { formTemplateId =>
        gformConnector.saveNotificationBannerFormTemplates(
          GformNotificationBannerFormTemplate(formTemplateId, bannerId)
        )
      }
      gformConnector.findNotificationBanner().map { _ =>
        Redirect(
          uk.gov.hmrc.eeittadminfrontend.controllers.routes.NotificationBannerController.notificationBanner
        )
      }
    }

}
