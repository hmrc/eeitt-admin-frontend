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
import play.api.data.Forms.{ optional, text }
import play.api.data.{ Form, Forms }
import play.api.i18n.I18nSupport
import play.api.mvc.MessagesControllerComponents
import uk.gov.hmrc.eeittadminfrontend.connectors.GformConnector
import uk.gov.hmrc.eeittadminfrontend.models._
import uk.gov.hmrc.eeittadminfrontend.models.sdes.{ ProcessingStatus, SdesConfig, SubmissionRef }
import uk.gov.hmrc.govukfrontend.views.Aliases.Text
import uk.gov.hmrc.govukfrontend.views.html.components
import uk.gov.hmrc.govukfrontend.views.viewmodels.content
import uk.gov.hmrc.govukfrontend.views.viewmodels.errormessage.ErrorMessage
import uk.gov.hmrc.govukfrontend.views.viewmodels.errorsummary.{ ErrorLink, ErrorSummary }
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class SdesWorkItemController @Inject() (
  frontendAuthComponents: FrontendAuthComponents,
  gformConnector: GformConnector,
  messagesControllerComponents: MessagesControllerComponents,
  sdes_workitem: uk.gov.hmrc.eeittadminfrontend.views.html.sdes_workitem,
  sdes_workitem_confirmation: uk.gov.hmrc.eeittadminfrontend.views.html.sdes_workitem_confirmation,
  sdesConfig: SdesConfig
)(implicit ec: ExecutionContext)
    extends GformAdminFrontendController(frontendAuthComponents, messagesControllerComponents) with I18nSupport {

  private val pageSize = 100
  private val logger: Logger = LoggerFactory.getLogger(getClass)

  def sdesWorkItem(
    page: Int,
    formTemplateId: Option[FormTemplateId],
    status: Option[ProcessingStatus]
  ) =
    authAction.async { implicit request =>
      gformConnector.searchWorkItem(page, pageSize, formTemplateId, status).map { sdesWorkItemPageData =>
        val pagination = Pagination(sdesWorkItemPageData.count, page, sdesWorkItemPageData.count.toInt, pageSize)
        Ok(sdes_workitem(pagination, sdesWorkItemPageData, formTemplateId, status))
      }
    }

  def enqueue(page: Int, id: String, submissionRef: SubmissionRef) =
    authAction.async { implicit request =>
      val username = request.retrieval
      logger.info(s"${username.value} sends a reprocess request for $id, submission id: ${submissionRef.value}")
      gformConnector.enqueueWorkItem(id).map { response =>
        val status = response.status
        if (status >= 200 && status < 300) {
          Redirect(routes.SdesWorkItemController.sdesWorkItem(0, None, None))
            .flashing(
              "success" -> s"Submission successfully reprocessed. Submission id: ${submissionRef.value}"
            )
        } else {
          Redirect(routes.SdesWorkItemController.sdesWorkItem(page, None, None))
            .flashing(
              "failed" -> s"Unexpected response with id: $id, submission id: ${submissionRef.value} : ${response.body}"
            )
        }
      }
    }

  def requestRemoval(id: String) =
    authAction.async { implicit request =>
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
      gformConnector.getSdesWorkItem(id).map { sdesWorkItemData =>
        Ok(sdes_workitem_confirmation(sdesWorkItemData, pageError, fieldErrors))
      }
    }

  private val formRemoval: Form[String] = Form(
    Forms.single(
      "remove" -> Forms.nonEmptyText
    )
  )

  def confirmRemoval(id: String) = authAction.async { implicit request =>
    formRemoval
      .bindFromRequest()
      .fold(
        _ =>
          Redirect(
            routes.SdesWorkItemController.requestRemoval(id)
          ).flashing("removeParamMissing" -> "true").pure[Future],
        {
          case "Yes" =>
            gformConnector
              .deleteSdesWorkItem(id)
              .map(_ =>
                Redirect(routes.SdesWorkItemController.sdesWorkItem(0, None, None))
                  .flashing(
                    "success" -> s"Sdes work-item successfully deleted."
                  )
              )
          case "No" =>
            Redirect(routes.SdesWorkItemController.sdesWorkItem(0, None, None)).pure[Future]
        }
      )
  }

  private val form: Form[(Option[String], Option[String])] = play.api.data.Form(
    Forms.tuple(
      "formTemplateId"   -> optional(text),
      "processingStatus" -> optional(text)
    )
  )

  def requestSearch(page: Int) = authAction.async { implicit request =>
    form
      .bindFromRequest()
      .fold(
        _ =>
          Redirect(
            routes.SdesWorkItemController.sdesWorkItem(page, None, None)
          ).pure[Future],
        {
          case (maybeFormTemplateId, maybeStatus) =>
            Redirect(
              routes.SdesWorkItemController.sdesWorkItem(
                0,
                maybeFormTemplateId.map(FormTemplateId(_)),
                maybeStatus.flatMap(ProcessingStatus.fromName)
              )
            ).pure[Future]
          case _ =>
            Redirect(
              routes.SdesWorkItemController.sdesWorkItem(page, None, None)
            ).pure[Future]
        }
      )
  }

}
