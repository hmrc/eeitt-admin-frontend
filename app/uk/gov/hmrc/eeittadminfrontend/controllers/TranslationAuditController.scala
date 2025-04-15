/*
 * Copyright 2025 HM Revenue & Customs
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
import play.api.mvc.MessagesControllerComponents

import uk.gov.hmrc.eeittadminfrontend.connectors.GformConnector
import uk.gov.hmrc.eeittadminfrontend.translation.TranslationAuditId
import uk.gov.hmrc.eeittadminfrontend.utils.DateUtils
import uk.gov.hmrc.govukfrontend.views.html.components.{ HeadCell, HtmlContent, Table, TableRow, Text }
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.eeittadminfrontend.views.html.translation_link_download_audit

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class TranslationAuditController @Inject() (
  frontendAuthComponents: FrontendAuthComponents,
  gformConnector: GformConnector,
  messagesControllerComponents: MessagesControllerComponents,
  translation_audit_landing: uk.gov.hmrc.eeittadminfrontend.views.html.translation_audit_landing
)(implicit ec: ExecutionContext)
    extends GformAdminFrontendController(frontendAuthComponents, messagesControllerComponents) with I18nSupport {

  def translationAuditDownload(translationAuditId: TranslationAuditId) =
    authorizedRead.async { implicit request =>
      gformConnector
        .translationAuditDownload(translationAuditId)
        .map { streamedResponse =>
          Ok.chunked(streamedResponse.bodyAsSource)
            .withHeaders(
              streamedResponse.headers.view.mapValues(_.head).toList: _*
            )
        }
    }

  def translationAudit() = authorizedRead.async { implicit request =>
    gformConnector.translationAuditOverview().map { translationAuditOverviews =>
      val tableRows = translationAuditOverviews.map { translationAuditOverview =>
        val downloadLink = routes.TranslationAuditController.translationAuditDownload(translationAuditOverview._id)
        Seq(
          TableRow(content = Text(translationAuditOverview.formTemplateId.value)),
          TableRow(content = Text(translationAuditOverview.result.report())),
          TableRow(content = Text(DateUtils.formatInstant(translationAuditOverview.createdAt))),
          TableRow(content = HtmlContent(translation_link_download_audit(downloadLink)))
        )

      }
      val table = Table(
        rows = tableRows,
        head = Some(
          Seq(
            HeadCell(content = Text("FormTemplate ID")),
            HeadCell(content = Text("Result")),
            HeadCell(content = Text("Created at")),
            HeadCell(content = Text("Action"))
          )
        ),
        firstCellIsHeader = false
      )
      Ok(translation_audit_landing(table))
    }
  }
}
