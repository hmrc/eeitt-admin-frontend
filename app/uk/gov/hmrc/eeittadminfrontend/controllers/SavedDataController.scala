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
import play.api.i18n.I18nSupport
import play.api.libs.json.{ JsArray, JsString }
import play.api.mvc.MessagesControllerComponents
import uk.gov.hmrc.eeittadminfrontend.connectors.GformConnector
import uk.gov.hmrc.eeittadminfrontend.models.FormTemplateId
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents

import scala.concurrent.ExecutionContext

class SavedDataController @Inject() (
  frontendAuthComponents: FrontendAuthComponents,
  gformConnector: GformConnector,
  messagesControllerComponents: MessagesControllerComponents,
  saved_data: uk.gov.hmrc.eeittadminfrontend.views.html.saved_data,
  saved_data_details: uk.gov.hmrc.eeittadminfrontend.views.html.saved_data_details,
  saved_data_formtemplates: uk.gov.hmrc.eeittadminfrontend.views.html.saved_data_formtemplates
)(implicit ec: ExecutionContext)
    extends GformAdminFrontendController(frontendAuthComponents, messagesControllerComponents) with I18nSupport {

  def savedData() =
    authAction.async { implicit request =>
      gformConnector.getAllGformsTemplates.map {
        case JsArray(formTemplateIds) =>
          val ftIds: Seq[FormTemplateId] = formTemplateIds.collect {
            case JsString(id) if !id.startsWith("specimen-") => FormTemplateId(id)
          }.toSeq
          Ok(saved_data_formtemplates(ftIds.sortBy(_.value)))
        case other => BadRequest("Cannot retrieve form templates. Expected JsArray, got: " + other)
      }
    }

  def findSavedData(formTemplateId: FormTemplateId) =
    authAction.async { implicit request =>
      gformConnector.getFormCount(formTemplateId).map { case savedData =>
        Ok(saved_data(savedData))
      }
    }

  def findSavedDataDetails(formTemplateId: FormTemplateId) =
    authAction.async { implicit request =>
      gformConnector.getFormDetailCount(formTemplateId).map { case savedDataDetails =>
        Ok(saved_data_details(formTemplateId, savedDataDetails))
      }
    }

}
