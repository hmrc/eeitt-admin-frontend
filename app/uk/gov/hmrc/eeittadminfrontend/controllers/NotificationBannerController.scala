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

import cats.syntax.all._
import javax.inject.Inject
import play.api.data.Form
import play.api.data.Forms.{ mapping, text }
import play.api.i18n.I18nSupport
import play.api.mvc.MessagesControllerComponents
import scala.concurrent.{ ExecutionContext, Future }
import uk.gov.hmrc.eeittadminfrontend.connectors.GformConnector
import uk.gov.hmrc.eeittadminfrontend.models.GformNotificationBanner
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.Text
import uk.gov.hmrc.govukfrontend.views.viewmodels.errormessage.ErrorMessage
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents

class NotificationBannerController @Inject() (
  frontendAuthComponents: FrontendAuthComponents,
  gformConnector: GformConnector,
  messagesControllerComponents: MessagesControllerComponents,
  notification_banner_new: uk.gov.hmrc.eeittadminfrontend.views.html.notification_banner_new,
  notification_banner_existing: uk.gov.hmrc.eeittadminfrontend.views.html.notification_banner_existing
)(implicit ec: ExecutionContext)
    extends GformAdminFrontendController(frontendAuthComponents, messagesControllerComponents) with I18nSupport {

  val gformNotificationBannerForm: Form[GformNotificationBanner] = Form(
    mapping("message" -> text)(GformNotificationBanner.apply)(GformNotificationBanner.unapply)
  )

  def notificationBanner() =
    authAction.async { implicit request =>
      gformConnector.findNotificationBanner.map { maybeNotificationBanner =>
        maybeNotificationBanner.fold(Ok(notification_banner_new(Option.empty[ErrorMessage])))(gformNotificationBanner =>
          Ok(notification_banner_existing(gformNotificationBanner.toNotificationBanner))
        )
      }
    }

  def deleteNotificationBanner() =
    authAction.async { request =>
      gformConnector.deleteNotificationBanner.map { maybeNotificationBanner =>
        Redirect(
          uk.gov.hmrc.eeittadminfrontend.controllers.routes.NotificationBannerController.notificationBanner()
        )
      }
    }

  def saveNotificationBanner() =
    authAction.async { implicit request =>
      gformNotificationBannerForm
        .bindFromRequest()
        .fold(
          formWithErrors => Future.successful(BadRequest("Error with notification banner form binding")),
          gformNotificationBanner =>
            if (gformNotificationBanner.message.isEmpty) {
              Ok(notification_banner_new(Some(ErrorMessage(content = Text("Notification banner cannot be empty")))))
                .pure[Future]
            } else {
              gformConnector.saveNotificationBanner(gformNotificationBanner).map { _ =>
                Redirect(
                  uk.gov.hmrc.eeittadminfrontend.controllers.routes.NotificationBannerController
                    .notificationBanner()
                )
              }
            }
        )
    }
}
