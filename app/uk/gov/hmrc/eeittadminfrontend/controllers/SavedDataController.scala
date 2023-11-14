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
import play.api.libs.json.{ JsArray, JsNumber, JsString, JsValue }
import play.api.mvc.MessagesControllerComponents

import uk.gov.hmrc.eeittadminfrontend.connectors.GformConnector
import uk.gov.hmrc.eeittadminfrontend.models.{ CountData, FormTemplateId, VersionStats }
import uk.gov.hmrc.govukfrontend.views.html.components.{ HeadCell, Table, TableRow, Text }
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
      gformConnector.getAllSavedVersions.flatMap { allSavedVersions =>
        gformConnector.getAllGformsTemplates.map {
          case JsArray(formTemplateIds) =>
            val ftIds: Seq[FormTemplateId] = formTemplateIds.collect {
              case JsString(id) if !id.startsWith("specimen-") => FormTemplateId(id)
            }.toSeq
            Ok(saved_data_formtemplates(allSavedVersions, ftIds.sortBy(_.value)))
          case other => BadRequest("Cannot retrieve form templates. Expected JsArray, got: " + other)
        }
      }
    }

  private def versionedRow(version: JsValue, emailCount: Long, ggCount: Long): Seq[TableRow] = Seq(
    TableRow(
      content = Text(version.toString)
    ),
    TableRow(
      content = Text(emailCount.toString)
    ),
    TableRow(
      content = Text(ggCount.toString)
    )
  )

  def findSavedData(formTemplateId: FormTemplateId) =
    authAction.async { implicit request =>
      for {
        formTemplate <- gformConnector.getGformsTemplate(formTemplateId)
        savedData    <- gformConnector.getFormCount(formTemplateId)
      } yield {
        val latestVersion: Long =
          formTemplate.fold(
            error => throw new Exception(s"Error fetching formTemplate: $formTemplateId"),
            jsValue => (jsValue \ "version").as[Long]
          )

        val emptyLatestVersionStats = VersionStats(JsNumber(latestVersion), List.empty[CountData])

        val versionStats: List[VersionStats] = savedData.toList

        val latestVersionDataExists = savedData.map(_.version).toSet(JsNumber(latestVersion))

        val hasSavedData: Boolean = savedData.exists(_.stats.exists(_.count > 1))

        val stats: List[VersionStats] =
          if (latestVersionDataExists) versionStats else emptyLatestVersionStats :: versionStats

        val statsRows = stats.map { versionStats =>
          val version = versionStats.version
          val lookup: Map[Boolean, Long] =
            versionStats.stats.map(stat => stat.isEmail -> stat.count).toMap

          versionedRow(
            version,
            lookup.getOrElse(true, 0L), // true represent email
            lookup.getOrElse(false, 0L) // false represent gg
          )
        }

        val head = Some(
          Seq(
            HeadCell(
              content = Text("Version")
            ),
            HeadCell(
              content = Text("Count of email forms")
            ),
            HeadCell(
              content = Text("Count of gov gateway forms")
            )
          )
        )
        val versionedTable = Table(
          rows = statsRows,
          head = head
        )

        Ok(saved_data(formTemplateId, hasSavedData, versionedTable))
      }

    }

  def findSavedDataDetails(formTemplateId: FormTemplateId) =
    authAction.async { implicit request =>
      gformConnector.getFormDetailCount(formTemplateId).map { case savedDataDetails =>
        Ok(saved_data_details(formTemplateId, savedDataDetails))
      }
    }

}
