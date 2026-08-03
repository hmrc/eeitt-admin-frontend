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
import play.api.data.Forms.{ boolean, nonEmptyText, optional, text }
import play.api.data.{ Form, Forms }
import play.api.i18n.I18nSupport
import play.api.mvc.{ Action, AnyContent, MessagesControllerComponents, Result }
import play.twirl.api.Html
import uk.gov.hmrc.eeittadminfrontend.connectors.GformConnector
import uk.gov.hmrc.eeittadminfrontend.deployment.ContentValue
import uk.gov.hmrc.eeittadminfrontend.diff.{ DiffConfig, DiffMaker }
import uk.gov.hmrc.eeittadminfrontend.models._
import uk.gov.hmrc.eeittadminfrontend.models.fileupload.EnvelopeId
import uk.gov.hmrc.eeittadminfrontend.models.logging.CustomerDataAccessLog
import uk.gov.hmrc.eeittadminfrontend.models.sdes.SdesDestination._
import uk.gov.hmrc.eeittadminfrontend.models.sdes.{ ProcessingStatus, SdesDestination, SubmissionRef }
import uk.gov.hmrc.eeittadminfrontend.services.GformService
import uk.gov.hmrc.govukfrontend.views.Aliases.Text
import uk.gov.hmrc.govukfrontend.views.html.components
import uk.gov.hmrc.govukfrontend.views.viewmodels.content
import uk.gov.hmrc.govukfrontend.views.viewmodels.errormessage.ErrorMessage
import uk.gov.hmrc.govukfrontend.views.viewmodels.errorsummary.{ ErrorLink, ErrorSummary }
import uk.gov.hmrc.internalauth.client.{ AuthenticatedRequest, FrontendAuthComponents, Retrieval }

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class WorkItemController @Inject() (
  frontendAuthComponents: FrontendAuthComponents,
  gformConnector: GformConnector,
  gformService: GformService,
  messagesControllerComponents: MessagesControllerComponents,
  workitem: uk.gov.hmrc.eeittadminfrontend.views.html.workitem,
  workitem_confirmation: uk.gov.hmrc.eeittadminfrontend.views.html.workitem_confirmation,
  workitem_edit_confirmation: uk.gov.hmrc.eeittadminfrontend.views.html.workitem_edit_confirmation,
  workitem_edit_async: uk.gov.hmrc.eeittadminfrontend.views.html.workitem_edit_async,
  workitem_edit_async_diff: uk.gov.hmrc.eeittadminfrontend.views.html.workitem_edit_async_diff,
  workitem_history: uk.gov.hmrc.eeittadminfrontend.views.html.workitem_history,
  diffConfig: DiffConfig
)(implicit ec: ExecutionContext)
    extends GformAdminFrontendController(frontendAuthComponents, messagesControllerComponents) with I18nSupport {

  private val pageSize = 100
  private val logger: Logger = LoggerFactory.getLogger(getClass)

  def searchWorkItem(
    destination: SdesDestination,
    page: Int,
    formTemplateId: Option[FormTemplateId],
    status: Option[ProcessingStatus]
  ) =
    authorizedRead.async { implicit request =>
      gformConnector.searchWorkItem(destination, page, pageSize, formTemplateId, status).map { sdesWorkItemPageData =>
        val pagination = Pagination(sdesWorkItemPageData.count, page, sdesWorkItemPageData.count.toInt, pageSize)
        Ok(workitem(destination, pagination, sdesWorkItemPageData, formTemplateId, status))
      }
    }

  def searchWorkItemHistory(
    page: Int,
    envelopeId: Option[EnvelopeId],
    formTemplateId: Option[FormTemplateId],
    showFailuresOnly: Option[Boolean]
  ) =
    authorizedRead.async { implicit request =>
      gformConnector.searchWorkItemHistory(page, pageSize, envelopeId, formTemplateId, showFailuresOnly).map {
        workItemHistoryPageData =>
          val pagination =
            Pagination(workItemHistoryPageData.count, page, workItemHistoryPageData.count.toInt, pageSize)
          Ok(workitem_history(pagination, workItemHistoryPageData, envelopeId, formTemplateId, showFailuresOnly))
      }
    }

  def enqueue(destination: SdesDestination, page: Int, id: String, submissionRef: SubmissionRef) =
    authorizedWrite.async { implicit request =>
      val username = request.retrieval
      logger.info(s"${username.value} sends a reprocess request for $id, submission id: ${submissionRef.value}")
      gformConnector.enqueueWorkItem(destination, id).map { response =>
        val status = response.status
        if (status >= 200 && status < 300) {
          Redirect(routes.WorkItemController.searchWorkItem(destination, 0, None, None))
            .flashing(
              "success" -> s"Submission successfully reprocessed. Submission id: ${submissionRef.value}"
            )
        } else {
          Redirect(routes.WorkItemController.searchWorkItem(destination, page, None, None))
            .flashing(
              "failed" -> s"Unexpected response with id: $id, submission id: ${submissionRef.value} : ${response.body}"
            )
        }
      }
    }

  def requestRemoval(destination: SdesDestination, id: String) =
    authorizedDelete.async { implicit request =>
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
      gformConnector.getWorkItem(destination, id).map { workItemData =>
        Ok(workitem_confirmation(workItemData, pageError, fieldErrors))
      }
    }

  private val formRemoval: Form[String] = Form(
    Forms.single(
      "remove" -> Forms.nonEmptyText
    )
  )

  def confirmRemoval(destination: SdesDestination, id: String) = authorizedDelete.async { implicit request =>
    formRemoval
      .bindFromRequest()
      .fold(
        _ =>
          Redirect(
            routes.WorkItemController.requestRemoval(destination, id)
          ).flashing("removeParamMissing" -> "true").pure[Future],
        {
          case "Yes" =>
            gformConnector
              .deleteWorkItem(destination, id)
              .map(_ =>
                Redirect(routes.WorkItemController.searchWorkItem(destination, 0, None, None))
                  .flashing(
                    "success" -> s"Work-item successfully deleted."
                  )
              )
          case "No" =>
            Redirect(routes.WorkItemController.searchWorkItem(destination, 0, None, None)).pure[Future]
        }
      )
  }

  private def username(implicit request: AuthenticatedRequest[AnyContent, Retrieval.Username]): String =
    request.retrieval.value

  def requestEdit(destination: SdesDestination, id: String): Action[AnyContent] =
    authorizedDataAccess.async { implicit request =>
      val (pageError, fieldErrors) =
        request.flash.get("accessReasonParamMissing").fold((NoErrors: HasErrors, Map.empty[String, ErrorMessage])) {
          _ =>
            (
              Errors(
                new components.GovukErrorSummary()(
                  ErrorSummary(
                    errorList = List(
                      ErrorLink(
                        href = Some("#accessReason"),
                        content =
                          content.Text("You must enter a valid incident code or reason to view and edit this data")
                      )
                    ),
                    title = content.Text(request.messages.messages("generic.error.selectOption.heading"))
                  )
                )
              ),
              Map(
                "accessReason" -> ErrorMessage(
                  content = Text("You must enter a valid incident code or reason to view and edit this data")
                )
              )
            )
        }
      gformConnector.getWorkItem(destination, id).map { workItemData =>
        Ok(workitem_edit_confirmation(workItemData, pageError, fieldErrors))
      }
    }

  private val formAccessReason: Form[String] = Form(
    Forms.single(
      "accessReason" -> Forms.nonEmptyText
    )
  )

  def confirmEdit(destination: SdesDestination, id: String): Action[AnyContent] = authorizedDataAccess.async {
    implicit request =>
      formAccessReason
        .bindFromRequest()
        .fold(
          _ =>
            Redirect(
              routes.WorkItemController.requestEdit(destination, id)
            ).flashing("accessReasonParamMissing" -> "true").pure[Future],
          reason =>
            gformConnector.getAsyncWorkItem(id).map { asyncWorkItem =>
              gformService.logSensitiveDataAccess(
                CustomerDataAccessLog(
                  username,
                  s"editing async work item payload for ${asyncWorkItem.destinationId}",
                  reason,
                  asyncWorkItem.envelopeId.value
                )
              )
              Redirect(routes.WorkItemController.startEdit(id))
            }
        )
  }

  private val formPayloadEdit: Form[String] = Form(
    Forms.single(
      "updatedPayload" -> Forms.nonEmptyText
    )
  )

  def startEdit(id: String): Action[AnyContent] = authorizedDataAccess.async { implicit request =>
    // TODO: Error handling etc
    gformConnector.getAsyncWorkItem(id).map { asyncWorkItemData =>
      Ok(workitem_edit_async(asyncWorkItemData, asyncWorkItemData.payload))
    }
  }

  def differenceCheck(id: String) = authorizedDataAccess.async { implicit request =>
    formPayloadEdit
      .bindFromRequest()
      .fold(
        _ =>
          Redirect(
            routes.WorkItemController.startEdit(id)
          ).pure[Future],
        updatedPayload => showDiff(id, updatedPayload, None, None)
//          gformConnector.getAsyncWorkItem(id).map { asyncWorkItemData =>
//            val originalContent: ContentValue =
//              io.circe.parser
//                .parse(asyncWorkItemData.payload)
//                .toOption
//                .fold[ContentValue](ContentValue.TextContent(asyncWorkItemData.payload))(ContentValue.JsonContent)
//
//            val maybeUpdatedContent: Option[ContentValue] =
//              io.circe.parser
//                .parse(updatedPayload)
//                .toOption
//                .map(ContentValue.JsonContent)
//
//            maybeUpdatedContent.fold {
//              val (pageError, fieldErrors) =
//                (
//                  Errors(
//                    new components.GovukErrorSummary()(
//                      ErrorSummary(
//                        errorList = List(
//                          ErrorLink(
//                            href = Some("#updatedPayload"),
//                            content =
//                              content.Text("Your payload is not valid JSON. Please correct the errors and try again.")
//                          )
//                        ),
//                        title = content.Text(request.messages.messages("generic.error.selectOption.heading"))
//                      )
//                    )
//                  ),
//                  Map(
//                    "invalidJson" -> ErrorMessage(
//                      content = Text("Your payload is not valid JSON. Please correct the errors and try again.")
//                    )
//                  )
//                )
//
//              Ok(workitem_edit_async(asyncWorkItemData, updatedPayload, pageError, fieldErrors))
//            } { updatedContent =>
//              val filename = asyncWorkItemData.formTemplateId.value + "-" + asyncWorkItemData.destinationId
//              val diff: String = DiffMaker.getDiff(
//                filename,
//                filename,
//                originalContent,
//                updatedContent,
//                diffConfig.timeout
//              )
//
//              val diffHtml =
//                if (diff.isEmpty) {
//                  uk.gov.hmrc.eeittadminfrontend.views.html.history_no_diff()
//                } else
//                  uk.gov.hmrc.eeittadminfrontend.views.html.deployment_diff(Html(diff))
//
//              println(s"CM: Diff: $diffHtml")
//              Ok(workitem_edit_async_diff(asyncWorkItemData, updatedPayload, diffHtml))
//            }
//          }
      )

  }

  private def showDiff(
    id: String,
    updatedPayload: String,
    maybePageError: Option[HasErrors],
    maybeFieldError: Option[Map[String, ErrorMessage]]
  )(implicit
    request: AuthenticatedRequest[AnyContent, Retrieval.Username]
  ): Future[Result] =
    gformConnector.getAsyncWorkItem(id).map { asyncWorkItemData =>
      val originalContent: ContentValue =
        io.circe.parser
          .parse(asyncWorkItemData.payload)
          .toOption
          .fold[ContentValue](ContentValue.TextContent(asyncWorkItemData.payload))(ContentValue.JsonContent)

      val maybeUpdatedContent: Option[ContentValue] =
        io.circe.parser
          .parse(updatedPayload)
          .toOption
          .map(ContentValue.JsonContent)

      maybeUpdatedContent.fold {
        val (pageError, fieldErrors) =
          (
            Errors(
              new components.GovukErrorSummary()(
                ErrorSummary(
                  errorList = List(
                    ErrorLink(
                      href = Some("#updatedPayload"),
                      content = content.Text("Your payload is not valid JSON. Please correct the errors and try again.")
                    )
                  ),
                  title = content.Text(request.messages.messages("generic.error.selectOption.heading"))
                )
              )
            ),
            Map(
              "invalidJson" -> ErrorMessage(
                content = Text("Your payload is not valid JSON. Please correct the errors and try again.")
              )
            )
          )

        Ok(workitem_edit_async(asyncWorkItemData, updatedPayload, pageError, fieldErrors))
      } { updatedContent =>
        val filename = asyncWorkItemData.formTemplateId.value + "-" + asyncWorkItemData.destinationId
        val diff: String = DiffMaker.getDiff(
          filename,
          filename,
          originalContent,
          updatedContent,
          diffConfig.timeout
        )

        val diffHtml =
          if (diff.isEmpty) {
            uk.gov.hmrc.eeittadminfrontend.views.html.history_no_diff()
          } else
            uk.gov.hmrc.eeittadminfrontend.views.html.deployment_diff(Html(diff))

        println(s"CM: Diff: $diffHtml")
        Ok(
          workitem_edit_async_diff(
            asyncWorkItemData,
            updatedPayload,
            diffHtml,
            maybePageError.getOrElse(NoErrors),
            maybeFieldError.getOrElse(Map.empty[String, ErrorMessage])
          )
        )
      }
    }

  private val formPayloadConfirm: Form[(Option[String], String)] = Form(
    Forms.tuple(
      "action"         -> optional(text),
      "updatedPayload" -> Forms.nonEmptyText
    )
  )

  def saveOrEdit(id: String): Action[AnyContent] = authorizedDataAccess.async { implicit request =>
    formPayloadConfirm
      .bindFromRequest()
      .fold(
        _ =>
          Redirect(
            routes.WorkItemController.startEdit(id)
          ).pure[Future],
        {
          case (Some("save"), updatedPayload) =>
            println("doing the save one")
            for {
              asyncWorkItemData <- gformConnector.getAsyncWorkItem(id)
              response <- gformConnector.updateAsyncWorkItem(
                            asyncWorkItemData.copy(payload = updatedPayload, username = Some(username))
                          )
            } yield
              if (response.status == 200) {
                Redirect(routes.WorkItemController.searchWorkItem(AsyncHandlebars, 0, None, None))
                  .flashing(
                    "success" -> s"Work-item successfully updated."
                  )
              } else {
                val (pageError, fieldErrors) =
                  (
                    Errors(
                      new components.GovukErrorSummary()(
                        ErrorSummary(
                          errorList = List(
                            ErrorLink(
                              href = Some("#updatedPayload"),
                              content = content.Text(
                                "There was an error saving your changes. Please correct the errors and try again."
                              )
                            )
                          ),
                          title = content.Text(request.messages.messages("generic.error.selectOption.heading"))
                        )
                      )
                    ),
                    Map(
                      "invalidJson" -> ErrorMessage(
                        content =
                          Text("There was an error saving your changes. Please correct the errors and try again.")
                      )
                    )
                  )
                Ok(workitem_edit_async(asyncWorkItemData, updatedPayload, pageError, fieldErrors))
              }
          case (Some("edit"), updatedPayload) =>
            gformConnector.getAsyncWorkItem(id).map { asyncWorkItemData =>
              Ok(workitem_edit_async(asyncWorkItemData, updatedPayload))
            }
          case (_, updatedPayload) =>
            val (pageError, fieldErrors) =
              (
                Errors(
                  new components.GovukErrorSummary()(
                    ErrorSummary(
                      errorList = List(
                        ErrorLink(
                          href = Some("#action"),
                          content = content.Text("Please select either 'Save' or 'Edit' to proceed.")
                        )
                      ),
                      title = content.Text(request.messages.messages("generic.error.selectOption.heading"))
                    )
                  )
                ),
                Map(
                  "saveOrEdit" -> ErrorMessage(
                    content = Text("Please select either 'Save' or 'Edit' to proceed.")
                  )
                )
              )
            showDiff(id, updatedPayload, Some(pageError), Some(fieldErrors))
        }
      )
  }

  private val form: Form[(String, Option[String], Option[String])] = play.api.data.Form(
    Forms.tuple(
      "sdesDestinationId" -> nonEmptyText,
      "formTemplateId"    -> optional(text),
      "processingStatus"  -> optional(text)
    )
  )

  private val historyForm: Form[(Option[String], Option[String], Option[Boolean])] = play.api.data.Form(
    Forms.tuple(
      "envelopeId"       -> optional(text),
      "formTemplateId"   -> optional(text),
      "showFailuresOnly" -> optional(boolean)
    )
  )

  def requestSearch(page: Int) = authorizedRead.async { implicit request =>
    form
      .bindFromRequest()
      .fold(
        _ =>
          Redirect(
            routes.WorkItemController.searchWorkItem(Dms, page, None, None)
          ).pure[Future],
        {
          case (destination, maybeFormTemplateId, maybeStatus) =>
            Redirect(
              routes.WorkItemController.searchWorkItem(
                SdesDestination.fromString(destination),
                0,
                maybeFormTemplateId.map(FormTemplateId(_)),
                maybeStatus.flatMap(ProcessingStatus.fromName)
              )
            ).pure[Future]
          case _ =>
            Redirect(
              routes.WorkItemController.searchWorkItem(Dms, page, None, None)
            ).pure[Future]
        }
      )
  }

  def requestSearchHistory(page: Int) = authorizedRead.async { implicit request =>
    historyForm
      .bindFromRequest()
      .fold(
        _ =>
          Redirect(
            routes.WorkItemController.searchWorkItemHistory(page, None, None, None)
          ).pure[Future],
        {
          case (maybeEnvelopeId, maybeFormTemplateId, maybeShowFailuresOnly) =>
            Redirect(
              routes.WorkItemController.searchWorkItemHistory(
                0,
                maybeEnvelopeId.map(EnvelopeId(_)),
                maybeFormTemplateId.map(FormTemplateId(_)),
                maybeShowFailuresOnly.map(_.booleanValue)
              )
            ).pure[Future]
          case _ =>
            Redirect(
              routes.WorkItemController.searchWorkItemHistory(page, None, None, None)
            ).pure[Future]
        }
      )
  }

}
