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
import uk.gov.hmrc.eeittadminfrontend.models.{ FormTemplateId, Shutter, ShutterMessageId, ShutterView }
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.Text
import uk.gov.hmrc.govukfrontend.views.viewmodels.errormessage.ErrorMessage
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import play.api.mvc

class ShutterController @Inject() (
  frontendAuthComponents: FrontendAuthComponents,
  gformConnector: GformConnector,
  messagesControllerComponents: MessagesControllerComponents,
  shutter: uk.gov.hmrc.eeittadminfrontend.views.html.shutter,
  shutter_form_templates: uk.gov.hmrc.eeittadminfrontend.views.html.shutter_form_templates
)(implicit ec: ExecutionContext)
    extends GformAdminFrontendController(frontendAuthComponents, messagesControllerComponents) with I18nSupport {

  val formShutter: Form[String] = Form(Forms.single("message" -> text))
  val formShutterForSpecificForm: Form[String] = Form(Forms.single("messageForTemplate" -> text))

  def shutter(): mvc.Action[mvc.AnyContent] =
    authorizedWrite.async { implicit request =>
      gformConnector.findShutter().map { shutters =>
        Ok(shutter(shutters, Option.empty[ErrorMessage]))
      }
    }

  def deleteShutter(shutterMessageId: ShutterMessageId) =
    authorizedWrite.async { implicit request =>
      gformConnector.deleteShutter(shutterMessageId).map { _ =>
        Redirect(
          uk.gov.hmrc.eeittadminfrontend.controllers.routes.ShutterController.shutter
        )
      }
    }

  def saveShutter() =
    authorizedWrite.async { implicit request =>
      formShutter
        .bindFromRequest()
        .fold(
          formWithErrors => Future.successful(BadRequest("Error with shutter form binding")),
          shutterMessage =>
            if (shutterMessage.isEmpty) {
              Ok(
                shutter(
                  List.empty[ShutterView],
                  Some(ErrorMessage(content = Text("shutter cannot be empty")))
                )
              )
                .pure[Future]
            } else {
              gformConnector
                .saveShutter(
                  Shutter(shutterMessage)
                )
                .map { _ =>
                  Redirect(
                    uk.gov.hmrc.eeittadminfrontend.controllers.routes.ShutterController.shutter
                  )
                }
            }
        )
    }

  def saveShutterForTemplate() =
    authorizedWrite.async { implicit request =>
      formShutterForSpecificForm
        .bindFromRequest()
        .fold(
          formWithErrors => Future.successful(BadRequest("Error with shutter form binding")),
          shutterMessage =>
            if (shutterMessage.isEmpty) {
              Ok(
                shutter(
                  List.empty[ShutterView],
                  Some(ErrorMessage(content = Text("shutter for a specific form cannot be empty")))
                )
              )
                .pure[Future]
            } else {
              gformConnector
                .saveShutter(
                  Shutter(shutterMessage)
                )
                .map { _ =>
                  Redirect(
                    uk.gov.hmrc.eeittadminfrontend.controllers.routes.ShutterController.shutter
                  )
                }
            }
        )
    }

  def addFormTemplate(shutterMessageId: ShutterMessageId) =
    authorizedWrite.async { implicit request =>
      for {
        excludeFormTemplateIds <- gformConnector.findShutter().map(_.flatMap(_.formTemplateIds))
        res <- gformConnector.getAllGformsTemplates.map {
                 case JsArray(formTemplateIds) =>
                   val ftIds: Seq[FormTemplateId] = formTemplateIds
                     .collect {
                       case JsString(id) if !id.startsWith("specimen-") => FormTemplateId(id)
                     }
                     .toSeq
                     .filter(formTemplateId => !excludeFormTemplateIds.contains(formTemplateId))
                     .sortBy(_.value)

                   Ok(shutter_form_templates(shutterMessageId, ftIds))
                 case other => BadRequest("Cannot retrieve form templates. Expected JsArray, got: " + other)
               }
      } yield res
    }
}
