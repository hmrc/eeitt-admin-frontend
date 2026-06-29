/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.eeittadminfrontend
package controllers

import play.api.i18n.I18nSupport
import play.api.mvc.MessagesControllerComponents
import play.twirl.api.Html

import uk.gov.hmrc.eeittadminfrontend.connectors.GformConnector
import uk.gov.hmrc.eeittadminfrontend.models.Pagination
import uk.gov.hmrc.eeittadminfrontend.models.logging.CustomerDataAccessLog
import uk.gov.hmrc.eeittadminfrontend.models.fileupload.EnvelopeId
import uk.gov.hmrc.eeittadminfrontend.utils.DateUtils
import uk.gov.hmrc.govukfrontend.views.html.components.{ HeadCell, HtmlContent, Table, TableRow, Text }
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class DataAccessController @Inject() (
  frontendAuthComponents: FrontendAuthComponents,
  gformConnector: GformConnector,
  messagesControllerComponents: MessagesControllerComponents,
  data_access: views.html.data_access
)(implicit ec: ExecutionContext)
    extends GformAdminFrontendController(frontendAuthComponents, messagesControllerComponents) with I18nSupport {

  private val pageSize = 100

  private def accessLogRow(accessLog: CustomerDataAccessLog): Seq[TableRow] = {

    val envelopeLinks: List[Html] = accessLog.envelopeIds
      .map(envelopeId => views.html.envelope_link(EnvelopeId(envelopeId)))

    val envelopeLinksHtml: Html = Html(envelopeLinks.map(_.body).mkString("<br>"))

    Seq(
      TableRow(
        content = Text(accessLog.userName)
      ),
      TableRow(
        content = Text(accessLog.sensitiveData)
      ),
      TableRow(
        content = Text(accessLog.reason)
      ),
      TableRow(
        content = HtmlContent(envelopeLinksHtml)
      ),
      TableRow(
        content = Text(DateUtils.formatInstant(accessLog.createdAt))
      )
    )
  }

  def dataAccess(page: Int) = authorizedRead.async { implicit request =>
    gformConnector.getDataAccessLog(page, pageSize).map { dataAccessLogPageData =>
      val dataAccessLogs = dataAccessLogPageData.dataAccessLogs
      val accessLogsRows = dataAccessLogs.map(accessLogRow)

      val head = Some(
        Seq(
          HeadCell(
            content = Text("Username")
          ),
          HeadCell(
            content = Text("Sensitive data")
          ),
          HeadCell(
            content = Text("Reason")
          ),
          HeadCell(
            content = Text("Envelope IDs")
          ),
          HeadCell(
            content = Text("Access date")
          )
        )
      )
      val accessLogsTable = Table(
        rows = accessLogsRows,
        head = head
      )

      val pagination =
        Pagination(dataAccessLogPageData.count, page, dataAccessLogPageData.count.toInt, pageSize)

      Ok(data_access(pagination, accessLogsTable))
    }

  }
}
