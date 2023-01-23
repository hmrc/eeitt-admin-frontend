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
import play.api.data
import play.api.i18n.I18nSupport
import play.api.mvc.MessagesControllerComponents
import uk.gov.hmrc.eeittadminfrontend.connectors.GformConnector
import uk.gov.hmrc.eeittadminfrontend.models._
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
  submission_sdes_confirmation: uk.gov.hmrc.eeittadminfrontend.views.html.submission_sdes_confirmation
)(implicit ec: ExecutionContext)
    extends GformAdminFrontendController(frontendAuthComponents, messagesControllerComponents) with I18nSupport {

  private val pageSize = 10
  private val daysBefore: Long = 30
  private val logger: Logger = LoggerFactory.getLogger(getClass)

  def sdesSubmissions(page: Int) =
    authAction.async { implicit request =>
      gformConnector.getSdesSubmissions(false, 0, page, pageSize).map { sdesSubmissionPageData =>
        val pagination = Pagination(sdesSubmissionPageData.count, page, sdesSubmissionPageData.count.toInt, pageSize)
        Ok(submission_sdes(pagination, sdesSubmissionPageData))
      }
    }

  def notifySDES(correlationId: CorrelationId, submissionRef: SubmissionRef, page: Int) =
    authAction.async { implicit request =>
      val username = request.retrieval
      logger.info(
        s"${username.value} sends a notification to SDES for correlation id ${correlationId.value}, submission id  ${submissionRef.value}"
      )
      gformConnector.notifySDES(correlationId).map { httpResponse =>
        Redirect(routes.SubmissionSdesController.sdesSubmissions(page))
          .flashing(
            "success" -> s"Envelope successfully notified. Correlation id: ${correlationId.value}, submission id: ${submissionRef.value}"
          )
      }
    }

  def requestRemoval(page: Int) =
    authAction.async { implicit request =>
      gformConnector.getSdesSubmissions(false, daysBefore, page, pageSize).map { sdesSubmissionPageData =>
        val pagination = Pagination(sdesSubmissionPageData.count, page, sdesSubmissionPageData.count.toInt, pageSize)

        val (pageError, fieldErrors) =
          request.flash.get("removeParamMissing").fold((NoErrors: HasErrors, Map.empty[String, ErrorMessage])) { _ =>
            (
              Errors(
                new components.GovukErrorSummary()(
                  ErrorSummary(
                    errorList = List(
                      ErrorLink(
                        href = Some("#remove"),
                        content = content.Text(request.messages.messages("generic.error.selectOption"))
                      )
                    ),
                    title = content.Text(request.messages.messages("generic.error.selectOption.heading"))
                  )
                )
              ),
              Map(
                "remove" -> ErrorMessage(
                  content = Text(request.messages.messages("generic.error.selectOption"))
                )
              )
            )
          }
        Ok(submission_sdes_confirmation(pagination, sdesSubmissionPageData, pageError, fieldErrors))
      }
    }

  private val form: data.Form[String] = play.api.data.Form(
    play.api.data.Forms.single(
      "remove" -> play.api.data.Forms.nonEmptyText
    )
  )

  def confirmRemoval(page: Int) = authAction.async { implicit request =>
    form
      .bindFromRequest()
      .fold(
        _ =>
          Redirect(
            routes.SubmissionSdesController.requestRemoval(page)
          ).flashing("removeParamMissing" -> "true").pure[Future],
        {
          case "Yes" =>
            gformConnector
              .deleteSdesSubmissions(daysBefore)
              .map(httpResponse =>
                Redirect(routes.SubmissionSdesController.sdesSubmissions(0))
                  .flashing(
                    "success" -> s"Sdes submissions successfully deleted."
                  )
              )
          case "No" =>
            Redirect(routes.SubmissionSdesController.sdesSubmissions(0)).pure[Future]
        }
      )
  }

}
