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

import cats.data.Validated
import cats.data.Validated.{ Invalid, Valid }
import cats.syntax.eq._
import cats.implicits.catsSyntaxApplicativeId
import org.slf4j.{ Logger, LoggerFactory }
import play.api.data.Forms.{ optional, text }
import play.api.data.{ Form, Forms }
import play.api.i18n.I18nSupport
import play.api.libs.json.Json
import play.api.mvc.{ MessagesControllerComponents, Request }
import play.twirl.api.Html
import uk.gov.hmrc.eeittadminfrontend.connectors.GformConnector
import uk.gov.hmrc.eeittadminfrontend.history.DateFilter
import uk.gov.hmrc.eeittadminfrontend.models._
import uk.gov.hmrc.eeittadminfrontend.models.sdes.{ CorrelationId, NotificationStatus, SdesConfig, SdesDestination, SdesFilter, SdesSubmissionPageData, SubmissionRef }
import uk.gov.hmrc.eeittadminfrontend.validators.DateValidator.validateDateFilter
import uk.gov.hmrc.eeittadminfrontend.views.components.DateTime.dateComponent
import uk.gov.hmrc.govukfrontend.views.Aliases.{ Button, Input, Label, Select, SelectItem, TableRow, Text }
import uk.gov.hmrc.govukfrontend.views.html.components
import uk.gov.hmrc.govukfrontend.views.html.components.GovukDateInput
import uk.gov.hmrc.govukfrontend.views.viewmodels.content
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.HtmlContent
import uk.gov.hmrc.govukfrontend.views.viewmodels.dateinput.DateInput
import uk.gov.hmrc.govukfrontend.views.viewmodels.errormessage.ErrorMessage
import uk.gov.hmrc.govukfrontend.views.viewmodels.errorsummary.{ ErrorLink, ErrorSummary }
import uk.gov.hmrc.govukfrontend.views.viewmodels.table.Table
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

  private def fromDateComponent(answers: Map[String, String], errors: Map[String, String]): DateInput =
    dateComponent("from", "Start date", answers, errors)

  private def toDateComponent(answers: Map[String, String], errors: Map[String, String]): DateInput =
    dateComponent("to", "End date", answers, errors)

  def sdesSubmissions(
    page: Int,
    isProcessed: Option[Boolean],
    searchKey: Option[String],
    status: Option[NotificationStatus],
    destination: Option[SdesDestination],
    fromDay: Option[String],
    fromMonth: Option[String],
    fromYear: Option[String],
    toDay: Option[String],
    toMonth: Option[String],
    toYear: Option[String]
  ) =
    authAction.async { implicit request =>
      val answers: Map[String, String] = Map(
        "from-day"   -> fromDay.getOrElse(""),
        "from-month" -> fromMonth.getOrElse(""),
        "from-year"  -> fromYear.getOrElse(""),
        "to-day"     -> toDay.getOrElse(""),
        "to-month"   -> toMonth.getOrElse(""),
        "to-year"    -> toYear.getOrElse("")
      ).collect {
        case (field, value) if value.trim.nonEmpty =>
          (field, value.trim)
      }

      val fromDateFilter: Validated[Map[String, String], Option[DateFilter]] =
        validateDateFilter("from", fromDay, fromMonth, fromYear, None, None)

      val toDateFilter: Validated[Map[String, String], Option[DateFilter]] =
        validateDateFilter("to", toDay, toMonth, toYear, None, None)

      val filter = SdesFilter(
        page,
        pageSize,
        isProcessed,
        searchKey,
        status,
        destination,
        None,
        None
      )
      (fromDateFilter, toDateFilter) match {
        case (Valid(fromDf), Valid(toDf)) =>
          val fromDateInput = fromDateComponent(answers, Map.empty[String, String])
          val toDateInput = toDateComponent(answers, Map.empty[String, String])
          showFilter(filter.copy(from = fromDf, to = toDf), page, fromDateInput, toDateInput, false)
        case (Invalid(fromErrors), Invalid(toErrors)) =>
          val fromDateInput = fromDateComponent(answers, fromErrors)
          val toDateInput = toDateComponent(answers, toErrors)
          showFilter(filter, page, fromDateInput, toDateInput, true)
        case (Valid(_), Invalid(toErrors)) =>
          val fromDateInput = fromDateComponent(answers, Map.empty[String, String])
          val toDateInput = toDateComponent(answers, toErrors)
          showFilter(filter, page, fromDateInput, toDateInput, true)
        case (Invalid(fromErrors), Valid(_)) =>
          val fromDateInput = fromDateComponent(answers, fromErrors)
          val toDateInput = toDateComponent(answers, Map.empty[String, String])
          showFilter(filter, page, fromDateInput, toDateInput, true)
      }
    }

  private def showFilter(
    filter: SdesFilter,
    page: Int,
    fromDateInput: DateInput,
    toDateInput: DateInput,
    hasError: Boolean
  )(implicit request: Request[_]) = {
    val filterTable = createFilterTable(filter: SdesFilter, fromDateInput, toDateInput)
    if (hasError) {
      val pagination =
        Pagination(0, page, 0, pageSize)

      Ok(
        submission_sdes(
          pagination,
          SdesSubmissionPageData.empty,
          filter.isProcessed,
          filter.searchKey,
          filter.status,
          filter.destination,
          filterTable
        )
      ).pure[Future]
    } else {
      gformConnector
        .getSdesSubmissions(filter)
        .map { sdesSubmissionPageData =>
          val pagination =
            Pagination(sdesSubmissionPageData.count, page, sdesSubmissionPageData.sdesSubmissions.size, pageSize)

          Ok(
            submission_sdes(
              pagination,
              sdesSubmissionPageData,
              filter.isProcessed,
              filter.searchKey,
              filter.status,
              filter.destination,
              filterTable
            )
          )
        }
    }
  }

  private def createFilterTable(filter: SdesFilter, fromDateInput: DateInput, toDateInput: DateInput) = {
    val govukErrorMessage: components.GovukErrorMessage = new components.GovukErrorMessage()
    val govukHint: components.GovukHint = new components.GovukHint()
    val govukLabel: components.GovukLabel = new components.GovukLabel()
    val govukFieldset: components.GovukFieldset = new components.GovukFieldset()
    val govukInput: components.GovukInput =
      new components.GovukInput(govukErrorMessage, govukHint, govukLabel)
    val govukSelect: components.GovukSelect =
      new components.GovukSelect(govukErrorMessage, govukHint, govukLabel)
    val govukButton: components.GovukButton = new components.GovukButton()
    val govukDateInput: components.GovukDateInput =
      new GovukDateInput(govukErrorMessage, govukHint, govukFieldset, govukInput)
    val tableAttributes = Map("style" -> "border:none;")

    def inputComponent(id: String, label: String, value: Option[String]) = govukInput(
      Input(
        id = id,
        name = id,
        value = value,
        label = Label(
          content = Text(label)
        )
      )
    )

    def createSelect(id: String, label: String, items: Seq[SelectItem]) = govukSelect(
      Select(
        id = id,
        name = id,
        items = items,
        label = Label(
          content = Text(label)
        )
      )
    )

    def tableRow(value: Html, additionalAttributes: Map[String, String] = Map.empty) =
      TableRow(attributes = tableAttributes ++ additionalAttributes, content = HtmlContent(value))

    val queryBySearchKey =
      tableRow(
        inputComponent(
          "searchKey",
          "Search key (Envelope Id/Submission Reference/Form Template Id)",
          filter.searchKey
        ),
        additionalAttributes = Map("colspan" -> "2")
      )

    val destinationItems = Seq(
      SelectItem(
        value = None,
        text = ""
      )
    ) ++ SdesDestination.values
      .map(d =>
        SelectItem(
          value = Some(d.toString),
          text = d.toString,
          selected = filter.destination.fold(false)(destination => d === destination)
        )
      )
      .toSeq
    val queryByDestination = tableRow(
      createSelect(
        "destination",
        "Destination",
        destinationItems
      )
    )

    val statusItems = Seq(
      SelectItem(
        value = None,
        text = ""
      )
    ) ++ NotificationStatus.values
      .map(ns =>
        SelectItem(
          value = Some(ns.toString),
          text = ns.toString,
          selected = filter.status.fold(false)(status => ns === status)
        )
      )
      .toSeq
    val queryByStatus = tableRow(
      createSelect(
        "notificationStatus",
        "Status",
        statusItems
      )
    )

    val queryFromDate = tableRow(
      govukDateInput(
        fromDateInput
      )
    )

    val queryToDate = tableRow(
      govukDateInput(
        toDateInput
      )
    )

    val queryButton = tableRow(
      govukButton(
        Button(
          preventDoubleClick = Some(true),
          content = Text("Search")
        )
      )
    )
    Table(
      rows = Seq(
        Seq(queryBySearchKey),
        Seq(queryByStatus, queryByDestination),
        Seq(queryFromDate, queryToDate),
        Seq(queryButton)
      )
    )
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
          Redirect(
            routes.SubmissionSdesController
              .sdesSubmissions(page, None, None, None, None, None, None, None, None, None, None)
          )
            .flashing(
              "success" -> s"Envelope successfully notified. Correlation id: ${correlationId.value}, submission id: ${submissionRef.value}"
            )
        } else {
          Redirect(
            routes.SubmissionSdesController
              .sdesSubmissions(page, None, None, None, None, None, None, None, None, None, None)
          )
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
                Redirect(
                  routes.SubmissionSdesController
                    .sdesSubmissions(0, None, None, None, None, None, None, None, None, None, None)
                )
                  .flashing(
                    "success" -> s"Sdes submission successfully updated."
                  )
              )
          case "No" =>
            Redirect(
              routes.SubmissionSdesController
                .sdesSubmissions(0, None, None, None, None, None, None, None, None, None, None)
            )
              .pure[Future]
        }
      )
  }

  private val form: Form[
    (
      Option[String],
      Option[String],
      Option[String],
      Option[String],
      Option[String],
      Option[String],
      Option[String],
      Option[String],
      Option[String]
    )
  ] =
    play.api.data.Form(
      Forms.tuple(
        "searchKey"          -> optional(text),
        "notificationStatus" -> optional(text),
        "destination"        -> optional(text),
        "from-day"           -> optional(text),
        "from-month"         -> optional(text),
        "from-year"          -> optional(text),
        "to-day"             -> optional(text),
        "to-month"           -> optional(text),
        "to-year"            -> optional(text)
      )
    )

  def requestSearch(page: Int) = authAction.async { implicit request =>
    form
      .bindFromRequest()
      .fold(
        _ =>
          Redirect(
            routes.SubmissionSdesController
              .sdesSubmissions(page, None, None, None, None, None, None, None, None, None, None)
          ).pure[Future],
        {
          case (
                maybeSearchKey,
                maybeStatus,
                maybeDestination,
                maybeFromDay,
                maybeFromMonth,
                maybeFromYear,
                maybeToDay,
                maybeToMonth,
                maybeToYear
              ) =>
            Redirect(
              routes.SubmissionSdesController.sdesSubmissions(
                0,
                None,
                maybeSearchKey,
                maybeStatus.map(NotificationStatus.fromString),
                maybeDestination.map(SdesDestination.fromString),
                maybeFromDay,
                maybeFromMonth,
                maybeFromYear,
                maybeToDay,
                maybeToMonth,
                maybeToYear
              )
            ).pure[Future]
          case _ =>
            Redirect(
              routes.SubmissionSdesController
                .sdesSubmissions(page, None, None, None, None, None, None, None, None, None, None)
            ).pure[Future]
        }
      )
  }

  def showHistory(correlationId: CorrelationId) =
    authAction.async { _ =>
      gformConnector.getSdesHistoryById(correlationId).map {
        case Right(payload) => Ok(Json.prettyPrint(Json.toJson(payload)))
        case Left(error)    => BadRequest(error)
      }
    }
}
