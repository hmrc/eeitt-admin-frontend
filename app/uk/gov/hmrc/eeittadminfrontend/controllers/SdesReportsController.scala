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
import org.slf4j.{ Logger, LoggerFactory }
import play.api.data.Forms.{ boolean, optional, text }
import play.api.data.{ Form, Forms }
import play.api.i18n.I18nSupport
import play.api.mvc.MessagesControllerComponents
import uk.gov.hmrc.eeittadminfrontend.connectors.SubmissionConsolidatorConnector
import uk.gov.hmrc.eeittadminfrontend.models._
import uk.gov.hmrc.eeittadminfrontend.models.sdes._
import uk.gov.hmrc.govukfrontend.views.Aliases.Text
import uk.gov.hmrc.govukfrontend.views.html.components
import uk.gov.hmrc.govukfrontend.views.viewmodels.content
import uk.gov.hmrc.govukfrontend.views.viewmodels.errormessage.ErrorMessage
import uk.gov.hmrc.govukfrontend.views.viewmodels.errorsummary.{ ErrorLink, ErrorSummary }
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class SdesReportsController @Inject() (
  frontendAuthComponents: FrontendAuthComponents,
  connector: SubmissionConsolidatorConnector,
  messagesControllerComponents: MessagesControllerComponents,
  sdes_reports: uk.gov.hmrc.eeittadminfrontend.views.html.sdes_reports,
  sdes_reports_confirmation: uk.gov.hmrc.eeittadminfrontend.views.html.sdes_reports_confirmation,
  sdesConfig: SdesConfig
)(implicit ec: ExecutionContext)
    extends GformAdminFrontendController(frontendAuthComponents, messagesControllerComponents) with I18nSupport {

  private val pageSize = 100
  private val logger: Logger = LoggerFactory.getLogger(getClass)

  def sdesSubmissions(
    page: Int,
    processed: Option[Boolean],
    status: Option[NotificationStatus],
    showBeforeAt: Option[Boolean]
  ) =
    authorizedRead.async { implicit request =>
      connector
        .getSdesSubmissions(page, pageSize, processed, status, showBeforeAt)
        .map { sdesReportPageData =>
          val pagination =
            Pagination(sdesReportPageData.count, page, sdesReportPageData.sdesSubmissions.size, pageSize)
          Ok(
            sdes_reports(
              pagination,
              sdesReportPageData,
              processed,
              status,
              showBeforeAt
            )
          )
        }
    }

  def notifySDES(correlationId: CorrelationId, submissionRef: SubmissionRef, page: Int) =
    authorizedWrite.async { implicit request =>
      val username = request.retrieval
      logger.info(
        s"${username.value} sends a notification to SDES for correlation id ${correlationId.value}, submission id  ${submissionRef.value}"
      )
      connector.notifySDES(correlationId).map { response =>
        val status = response.status
        if (status >= 200 && status < 300) {
          Redirect(routes.SdesReportsController.sdesSubmissions(page, None, None, None))
            .flashing(
              "success" -> s"Envelope successfully notified. Correlation id: ${correlationId.value}, submission id: ${submissionRef.value}"
            )
        } else {
          Redirect(routes.SdesReportsController.sdesSubmissions(page, None, None, None))
            .flashing(
              "failed" -> s"Unexpected SDES response with correlation id: ${correlationId.value}, submission id: ${submissionRef.value} : ${response.body}"
            )
        }
      }
    }

  def requestMark(correlationId: CorrelationId) =
    authorizedWrite.async { implicit request =>
      val (pageError, fieldErrors) =
        request.flash.get("markParamMissing").fold((NoErrors: HasErrors, Map.empty[String, ErrorMessage])) { _ =>
          (
            Errors(
              new components.GovukErrorSummary()(
                ErrorSummary(
                  errorList = List(
                    ErrorLink(
                      href = Some("#mark"),
                      content = content.Text(request.messages.messages("generic.error.selectOption"))
                    )
                  ),
                  title = content.Text(request.messages.messages("generic.error.selectOption.heading"))
                )
              )
            ),
            Map(
              "mark" -> ErrorMessage(
                content = Text(request.messages.messages("generic.error.selectOption"))
              )
            )
          )
        }
      connector.getSdesSubmission(correlationId).map { sdesReportData =>
        Ok(sdes_reports_confirmation(sdesReportData, sdesConfig.olderThan, pageError, fieldErrors))
      }
    }

  private val formMark: Form[String] = Form(
    Forms.single(
      "mark" -> Forms.nonEmptyText
    )
  )

  def confirmMark(correlationId: CorrelationId) = authorizedWrite.async { implicit request =>
    formMark
      .bindFromRequest()
      .fold(
        _ =>
          Redirect(
            routes.SdesReportsController.requestMark(correlationId)
          ).flashing("markParamMissing" -> "true").pure[Future],
        {
          case "Yes" =>
            connector
              .updateAsManualConfirmed(correlationId)
              .map(httpResponse =>
                Redirect(routes.SdesReportsController.sdesSubmissions(0, None, None, None))
                  .flashing(
                    "success" -> s"Sdes submission successfully deleted."
                  )
              )
          case "No" =>
            Redirect(routes.SdesReportsController.sdesSubmissions(0, None, None, None)).pure[Future]
        }
      )
  }

  private val form: Form[(Option[String], Option[Boolean])] = play.api.data.Form(
    Forms.tuple(
      "notificationStatus" -> optional(text),
      "showBeforeAt"       -> optional(boolean)
    )
  )

  def requestSearch(page: Int) = authorizedRead.async { implicit request =>
    form
      .bindFromRequest()
      .fold(
        _ =>
          Redirect(
            routes.SdesReportsController.sdesSubmissions(page, None, None, None)
          ).pure[Future],
        {
          case (maybeStatus, maybeShowBeforeAt) =>
            Redirect(
              routes.SdesReportsController.sdesSubmissions(
                0,
                None,
                maybeStatus.map(NotificationStatus.fromString),
                maybeShowBeforeAt
              )
            ).pure[Future]
          case _ =>
            Redirect(
              routes.SdesReportsController.sdesSubmissions(page, None, None, None)
            ).pure[Future]
        }
      )
  }

}
