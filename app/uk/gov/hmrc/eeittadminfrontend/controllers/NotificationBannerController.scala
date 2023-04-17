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
import play.api.data.{ Form, Forms }
import play.api.data.Forms.text
import play.api.i18n.I18nSupport
import play.api.libs.json.{ JsArray, JsString }
import play.api.mvc.MessagesControllerComponents

import scala.concurrent.{ ExecutionContext, Future }
import uk.gov.hmrc.eeittadminfrontend.connectors.GformConnector
import uk.gov.hmrc.eeittadminfrontend.models.{ FormTemplateId, GformNotificationBanner, GformNotificationBannerView }
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.Text
import uk.gov.hmrc.govukfrontend.views.viewmodels.errormessage.ErrorMessage
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents

class NotificationBannerController @Inject() (
  frontendAuthComponents: FrontendAuthComponents,
  gformConnector: GformConnector,
  messagesControllerComponents: MessagesControllerComponents,
  notification_banner: uk.gov.hmrc.eeittadminfrontend.views.html.notification_banner,
  notification_banner_form_templates: uk.gov.hmrc.eeittadminfrontend.views.html.notification_banner_form_templates
)(implicit ec: ExecutionContext)
    extends GformAdminFrontendController(frontendAuthComponents, messagesControllerComponents) with I18nSupport {

  val formNotificationBanner: Form[String] = Form(Forms.single("message" -> text))
  val formNotificationBannerForSpecificForm: Form[String] = Form(Forms.single("messageForTemplate" -> text))

  def notificationBanner() =
    authAction.async { implicit request =>
      gformConnector.findNotificationBanner().map { notificationBanners =>
        Ok(notification_banner(notificationBanners, None))
      }
    }

  def deleteNotificationBanner(id: String) =
    authAction.async { request =>
      gformConnector.deleteNotificationBanner(id).map { _ =>
        Redirect(
          uk.gov.hmrc.eeittadminfrontend.controllers.routes.NotificationBannerController.notificationBanner
        )
      }
    }

  def saveNotificationBanner() =
    authAction.async { implicit request =>
      formNotificationBanner
        .bindFromRequest()
        .fold(
          formWithErrors => Future.successful(BadRequest("Error with notification banner form binding")),
          notificationBannerMessage =>
            if (notificationBannerMessage.isEmpty) {
              Ok(
                notification_banner(
                  List.empty[GformNotificationBannerView],
                  Some(ErrorMessage(content = Text("Notification banner cannot be empty")))
                )
              )
                .pure[Future]
            } else {
              gformConnector
                .saveNotificationBanner(
                  GformNotificationBanner(notificationBannerMessage, true)
                )
                .map { _ =>
                  Redirect(
                    uk.gov.hmrc.eeittadminfrontend.controllers.routes.NotificationBannerController.notificationBanner
                  )
                }
            }
        )
    }

  def saveNotificationBannerForTemplate() =
    authAction.async { implicit request =>
      formNotificationBannerForSpecificForm
        .bindFromRequest()
        .fold(
          formWithErrors => Future.successful(BadRequest("Error with notification banner form binding")),
          notificationBannerMessage =>
            if (notificationBannerMessage.isEmpty) {
              Ok(
                notification_banner(
                  List.empty[GformNotificationBannerView],
                  Some(ErrorMessage(content = Text("Notification banner for a specific form cannot be empty")))
                )
              )
                .pure[Future]
            } else {
              gformConnector
                .saveNotificationBanner(
                  GformNotificationBanner(notificationBannerMessage, false)
                )
                .map { _ =>
                  Redirect(
                    uk.gov.hmrc.eeittadminfrontend.controllers.routes.NotificationBannerController.notificationBanner
                  )
                }
            }
        )
    }

  def addFormTemplate(id: String) =
    authAction.async { implicit request =>
      for {
        excludeFormTemplateIds <- gformConnector.findNotificationBanner().map(_.flatMap(_.formTemplateIds))
        res <- gformConnector.getAllGformsTemplates.map {
                 case JsArray(formTemplateIds) =>
                   val ftIds: Seq[FormTemplateId] = formTemplateIds
                     .collect {
                       case JsString(id) if !id.startsWith("specimen-") => FormTemplateId(id)
                     }
                     .toSeq
                     .filter(formTemplateId => !excludeFormTemplateIds.contains(formTemplateId))
                     .sortBy(_.value)

                   Ok(notification_banner_form_templates(id, ftIds))
                 case other => BadRequest("Cannot retrieve form templates. Expected JsArray, got: " + other)
               }
      } yield res
    }
}
