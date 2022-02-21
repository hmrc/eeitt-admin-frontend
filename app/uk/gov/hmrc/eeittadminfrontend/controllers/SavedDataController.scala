/*
 * Copyright 2022 HM Revenue & Customs
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

import play.api.i18n.I18nSupport
import play.api.libs.json.{ JsArray, JsString }
import play.api.mvc.MessagesControllerComponents
import uk.gov.hmrc.eeittadminfrontend.auth.AuthConnector
import uk.gov.hmrc.eeittadminfrontend.config.{ AppConfig, AuthAction }
import uk.gov.hmrc.eeittadminfrontend.connectors.GformConnector
import uk.gov.hmrc.eeittadminfrontend.models.FormTemplateId
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.ExecutionContext

class SavedDataController(
  val authConnector: AuthConnector,
  authAction: AuthAction,
  gformConnector: GformConnector,
  messagesControllerComponents: MessagesControllerComponents
)(implicit ec: ExecutionContext, appConfig: AppConfig)
    extends FrontendController(messagesControllerComponents) with I18nSupport {

  def savedData() =
    authAction.async { implicit request =>
      gformConnector.getAllGformsTemplates.map {
        case JsArray(formTemplateIds) =>
          val ftIds: Seq[FormTemplateId] = formTemplateIds.collect {
            case JsString(id) if !id.startsWith("specimen-") => FormTemplateId(id)
          }
          Ok(uk.gov.hmrc.eeittadminfrontend.views.html.saved_data_formtemplates(ftIds.sortBy(_.value)))
        case other => BadRequest("Cannot retrieve form templates. Expected JsArray, got: " + other)
      }
    }

  def findSavedData(formTemplateId: FormTemplateId) =
    authAction.async { implicit request =>
      gformConnector.getFormCount(formTemplateId).map { case savedData =>
        Ok(uk.gov.hmrc.eeittadminfrontend.views.html.saved_data(savedData))
      }
    }

}
