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

import play.api.i18n.I18nSupport
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.MessagesControllerComponents
import uk.gov.hmrc.eeittadminfrontend.connectors.GformConnector
import uk.gov.hmrc.govukfrontend.views.html.components.{ HeadCell, HtmlContent, NotificationBanner, Table, TableRow, Text }
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class SdesDestinationsMigrationController @Inject() (
  frontendAuthComponents: FrontendAuthComponents,
  gformConnector: GformConnector,
  messagesControllerComponents: MessagesControllerComponents,
  sdes_submissions_migration: uk.gov.hmrc.eeittadminfrontend.views.html.sdes_submissions_migration
)(implicit ec: ExecutionContext)
    extends GformAdminFrontendController(frontendAuthComponents, messagesControllerComponents) with I18nSupport {

  private def sdesDestinationRow(destination: JsValue, count: Long): Seq[TableRow] = Seq(
    TableRow(
      content = Text(Json.prettyPrint(destination))
    ),
    TableRow(
      content = Text(count.toString)
    )
  )

  def sdesDestinationsMigration(error: Option[String], success: Option[String]) = authAction.async { implicit request =>
    gformConnector
      .sdesDestinationsStats()
      .map { stats =>
        val statsRows = stats.map { stat =>
          sdesDestinationRow(
            stat.destination,
            stat.count
          )
        }
        val head = Some(
          Seq(
            HeadCell(
              content = Text("Destination")
            ),
            HeadCell(
              content = Text("Number of sdes submissions")
            )
          )
        )
        val versionedTable = Table(
          rows = statsRows,
          head = head
        )

        val errorBanner = error.map { e =>
          NotificationBanner(
            title = Text("Migration failure"),
            content = HtmlContent(e),
            bannerType = Some("failure"),
            role = Some("alert")
          )
        }

        val successBanner = success.map { e =>
          NotificationBanner(
            title = Text("Migration succeed"),
            content = HtmlContent(e),
            bannerType = Some("success"),
            role = Some("alert")
          )
        }

        Ok(sdes_submissions_migration(versionedTable, errorBanner, successBanner))
      }
  }

  def runMigration() =
    authAction.async { request =>
      gformConnector
        .runSdesMigration()
        .map { sdesReportPageData =>
          val redirectUrl = sdesReportPageData match {
            case Left(error) =>
              routes.SdesDestinationsMigrationController.sdesDestinationsMigration(Some(error), None)
            case Right(successMessage) =>
              routes.SdesDestinationsMigrationController.sdesDestinationsMigration(None, Some(successMessage))
          }
          Redirect(redirectUrl)
        }
    }
}
