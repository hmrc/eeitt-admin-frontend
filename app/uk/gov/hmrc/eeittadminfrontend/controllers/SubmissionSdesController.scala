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

import cats.implicits.catsSyntaxApplicativeId
import org.slf4j.{ Logger, LoggerFactory }
import play.api.data.Forms.{ boolean, optional, text }
import play.api.data.{ Form, Forms }
import play.api.i18n.I18nSupport
import play.api.mvc.MessagesControllerComponents
import uk.gov.hmrc.eeittadminfrontend.connectors.GformConnector
import uk.gov.hmrc.eeittadminfrontend.models._
import uk.gov.hmrc.eeittadminfrontend.models.sdes.{ CorrelationId, NotificationStatus, SdesConfig, SdesDestination, SubmissionRef }
import uk.gov.hmrc.govukfrontend.views.Aliases.Text
import uk.gov.hmrc.govukfrontend.views.html.components
import uk.gov.hmrc.govukfrontend.views.viewmodels.content
import uk.gov.hmrc.govukfrontend.views.viewmodels.errormessage.ErrorMessage
import uk.gov.hmrc.govukfrontend.views.viewmodels.errorsummary.{ ErrorLink, ErrorSummary }
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class SubmissionSdesController @Inject() (
  frontendAuthComponents: FrontendAuthComponents,
  gformConnector: GformConnector,
  messagesControllerComponents: MessagesControllerComponents,
  submission_sdes: uk.gov.hmrc.eeittadminfrontend.views.html.submission_sdes,
  submission_sdes_confirmation: uk.gov.hmrc.eeittadminfrontend.views.html.submission_sdes_confirmation,
  sdesConfig: SdesConfig
)(implicit ec: ExecutionContext)
    extends GformAdminFrontendController(frontendAuthComponents, messagesControllerComponents) with I18nSupport {

  private val pageSize = 100
  private val logger: Logger = LoggerFactory.getLogger(getClass)

  def sdesSubmissions(
    page: Int,
    processed: Option[Boolean],
    formTemplateId: Option[FormTemplateId],
    status: Option[NotificationStatus],
    showBeforeAt: Option[Boolean],
    destination: Option[SdesDestination]
  ) =
    authAction.async { implicit request =>
      gformConnector
        .getSdesSubmissions(page, pageSize, processed, formTemplateId, status, showBeforeAt, destination)
        .map { sdesSubmissionPageData =>
          val pagination =
            Pagination(sdesSubmissionPageData.count, page, sdesSubmissionPageData.sdesSubmissions.size, pageSize)
          Ok(
            submission_sdes(
              pagination,
              sdesSubmissionPageData,
              processed,
              formTemplateId,
              status,
              showBeforeAt,
              destination
            )
          )
        }
    }

  def notifySDES(correlationId: CorrelationId, submissionRef: SubmissionRef, page: Int) =
    authAction.async { implicit request =>
      val username = request.retrieval
      logger.info(
        s"${username.value} sends a notification to SDES for correlation id ${correlationId.value}, submission id  ${submissionRef.value}"
      )
      gformConnector.notifySDES(correlationId).map { response =>
        val status = response.status
        if (status >= 200 && status < 300) {
          Redirect(routes.SubmissionSdesController.sdesSubmissions(page, None, None, None, None, None))
            .flashing(
              "success" -> s"Envelope successfully notified. Correlation id: ${correlationId.value}, submission id: ${submissionRef.value}"
            )
        } else {
          Redirect(routes.SubmissionSdesController.sdesSubmissions(page, None, None, None, None, None))
            .flashing(
              "failed" -> s"Unexpected SDES response with correlation id: ${correlationId.value}, submission id: ${submissionRef.value} : ${response.body}"
            )
        }
      }
    }

  def requestMark(correlationId: CorrelationId) =
    authAction.async { implicit request =>
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
      gformConnector.getSdesSubmission(correlationId).map { sdesSubmissionData =>
        Ok(submission_sdes_confirmation(sdesSubmissionData, sdesConfig.olderThan, pageError, fieldErrors))
      }
    }

  private val formMark: Form[String] = Form(
    Forms.single(
      "mark" -> Forms.nonEmptyText
    )
  )

  def confirmMark(correlationId: CorrelationId) = authAction.async { implicit request =>
    formMark
      .bindFromRequest()
      .fold(
        _ =>
          Redirect(
            routes.SubmissionSdesController.requestMark(correlationId)
          ).flashing("markParamMissing" -> "true").pure[Future],
        {
          case "Yes" =>
            gformConnector
              .updateAsManualConfirmed(correlationId)
              .map(httpResponse =>
                Redirect(routes.SubmissionSdesController.sdesSubmissions(0, None, None, None, None, None))
                  .flashing(
                    "success" -> s"Sdes submission successfully updated."
                  )
              )
          case "No" =>
            Redirect(routes.SubmissionSdesController.sdesSubmissions(0, None, None, None, None, None)).pure[Future]
        }
      )
  }

  private val form: Form[(Option[String], Option[String], Option[Boolean], Option[String])] = play.api.data.Form(
    Forms.tuple(
      "formTemplateId"     -> optional(text),
      "notificationStatus" -> optional(text),
      "showBeforeAt"       -> optional(boolean),
      "destination"        -> optional(text)
    )
  )

  def requestSearch(page: Int) = authAction.async { implicit request =>
    form
      .bindFromRequest()
      .fold(
        _ =>
          Redirect(
            routes.SubmissionSdesController.sdesSubmissions(page, None, None, None, None, None)
          ).pure[Future],
        {
          case (maybeFormTemplateId, maybeStatus, maybeShowBeforeAt, maybeDestination) =>
            Redirect(
              routes.SubmissionSdesController.sdesSubmissions(
                0,
                None,
                maybeFormTemplateId.map(FormTemplateId(_)),
                maybeStatus.map(NotificationStatus.fromString),
                maybeShowBeforeAt,
                maybeDestination.map(SdesDestination.fromString)
              )
            ).pure[Future]
          case _ =>
            Redirect(
              routes.SubmissionSdesController.sdesSubmissions(page, None, None, None, None, None)
            ).pure[Future]
        }
      )
  }

}
