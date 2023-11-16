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

import cats.data.{ NonEmptyList, Validated }
import cats.data.Validated.{ Invalid, Valid }
import cats.syntax.all._
import java.time.{ LocalDate, LocalDateTime }
import play.api.libs.circe.Circe
import play.api.i18n.I18nSupport
import play.api.mvc.MessagesControllerComponents
import play.twirl.api.{ Html, HtmlFormat }
import scala.concurrent.Future
import scala.util.Try
import uk.gov.hmrc.eeittadminfrontend.connectors.GformConnector
import uk.gov.hmrc.eeittadminfrontend.deployment.ContentValue
import uk.gov.hmrc.eeittadminfrontend.diff.{ DiffConfig, DiffMaker }
import uk.gov.hmrc.eeittadminfrontend.history.{ DateFilter, HistoryComparison, HistoryFilter, HistoryId, HistoryOverview, HistoryOverviewFull }
import uk.gov.hmrc.eeittadminfrontend.models.FormTemplateRawId
import uk.gov.hmrc.eeittadminfrontend.utils.DateUtils
import uk.gov.hmrc.govukfrontend.views.html.components.{ HeadCell, HtmlContent, Table, TableRow, Text }
import uk.gov.hmrc.govukfrontend.views.viewmodels.content
import uk.gov.hmrc.govukfrontend.views.viewmodels.dateinput.{ DateInput, InputItem }
import uk.gov.hmrc.govukfrontend.views.viewmodels.errormessage.ErrorMessage
import uk.gov.hmrc.govukfrontend.views.viewmodels.fieldset.{ Fieldset, Legend }
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class HistoryController @Inject() (
  frontendAuthComponents: FrontendAuthComponents,
  gformConnector: GformConnector,
  messagesControllerComponents: MessagesControllerComponents,
  history_landing: uk.gov.hmrc.eeittadminfrontend.views.html.history_landing,
  history_by_form_template_id: uk.gov.hmrc.eeittadminfrontend.views.html.history_by_form_template_id,
  history_of: uk.gov.hmrc.eeittadminfrontend.views.html.history_of,
  history_by_date: uk.gov.hmrc.eeittadminfrontend.views.html.history_by_date,
  diffConfig: DiffConfig
)(implicit ec: ExecutionContext)
    extends GformAdminFrontendController(frontendAuthComponents, messagesControllerComponents) with I18nSupport
    with Circe {

  def history() = authAction.async { implicit request =>
    Future.successful(Ok(history_landing()))
  }

  def historyByDateTime(
    fromDay: Option[String],
    fromMonth: Option[String],
    fromYear: Option[String],
    fromHour: Option[String],
    fromMinute: Option[String],
    toDay: Option[String],
    toMonth: Option[String],
    toYear: Option[String],
    toHour: Option[String],
    toMinute: Option[String]
  ) =
    authAction.async { implicit request =>
      val answers: Map[String, String] = Map(
        "from-day"    -> fromDay.getOrElse(""),
        "from-month"  -> fromMonth.getOrElse(""),
        "from-year"   -> fromYear.getOrElse(""),
        "from-hour"   -> fromHour.getOrElse(""),
        "from-minute" -> fromMinute.getOrElse(""),
        "to-day"      -> toDay.getOrElse(""),
        "to-month"    -> toMonth.getOrElse(""),
        "to-year"     -> toYear.getOrElse(""),
        "to-hour"     -> toHour.getOrElse(""),
        "to-minute"   -> toMinute.getOrElse("")
      ).collect {
        case (field, value) if value.trim.nonEmpty =>
          (field, value.trim)
      }

      val maybeFromDay: Option[String] = answers.get("from-day")
      val maybeFromMonth: Option[String] = answers.get("from-month")
      val maybeFromYear: Option[String] = answers.get("from-year")
      val maybeFromHour: Option[String] = answers.get("from-hour")
      val maybeFromMinute: Option[String] = answers.get("from-minute")

      val maybeToDay: Option[String] = answers.get("to-day")
      val maybeToMonth: Option[String] = answers.get("to-month")
      val maybeToYear: Option[String] = answers.get("to-year")
      val maybeToHour: Option[String] = answers.get("to-hour")
      val maybeToMinute: Option[String] = answers.get("to-minute")

      val fromDateFilter: Validated[Map[String, String], Option[DateFilter]] =
        validateDateFilter("from", maybeFromDay, maybeFromMonth, maybeFromYear, maybeFromHour, maybeFromMinute)

      val toDateFilter: Validated[Map[String, String], Option[DateFilter]] =
        validateDateFilter("to", maybeToDay, maybeToMonth, maybeToYear, maybeToHour, maybeToMinute)

      (fromDateFilter, toDateFilter) match {
        case (Valid(fromDf), Valid(toDf)) =>
          val hf = HistoryFilter(fromDf, toDf)
          val fromDateInput = fromDateTimeComponent(answers, Map.empty[String, String])
          val toDateInput = toDateTimeComponent(answers, Map.empty[String, String])
          gformConnector.historyWithFilter(hf).map { historyOverviewFull =>
            val table = overviewTableFull(fullOverviewTableRows(historyOverviewFull))
            Ok(history_by_date(table, fromDateInput, toDateInput, historyOverviewFull.size))
          }
        case (Invalid(fromErrors), Invalid(toErrors)) =>
          val fromDateInput = fromDateTimeComponent(answers, fromErrors)
          val toDateInput = toDateTimeComponent(answers, toErrors)
          Future.successful(
            Ok(history_by_date(overviewTableFull(List.empty[Seq[TableRow]]), fromDateInput, toDateInput, 0))
          )
        case (Valid(_), Invalid(toErrors)) =>
          val fromDateInput = fromDateTimeComponent(answers, Map.empty[String, String])
          val toDateInput = toDateTimeComponent(answers, toErrors)
          Future.successful(
            Ok(history_by_date(overviewTableFull(List.empty[Seq[TableRow]]), fromDateInput, toDateInput, 0))
          )
        case (Invalid(fromErrors), Valid(_)) =>
          val fromDateInput = fromDateTimeComponent(answers, fromErrors)
          val toDateInput = toDateTimeComponent(answers, Map.empty[String, String])
          Future.successful(
            Ok(history_by_date(overviewTableFull(List.empty[Seq[TableRow]]), fromDateInput, toDateInput, 0))
          )
      }
    }

  def historyByDateTimePost() = authAction.async { implicit request =>
    val answers: Map[String, String] = request.body.asFormUrlEncoded
      .map(_.collect {
        case (field, value :: _) if value.trim.nonEmpty =>
          (field, value.trim)
      })
      .getOrElse(Map.empty[String, String])

    val fromDay: Option[String] = answers.get("from-day")
    val fromMonth: Option[String] = answers.get("from-month")
    val fromYear: Option[String] = answers.get("from-year")
    val fromHour: Option[String] = answers.get("from-hour")
    val fromMinute: Option[String] = answers.get("from-minute")

    val toDay: Option[String] = answers.get("to-day")
    val toMonth: Option[String] = answers.get("to-month")
    val toYear: Option[String] = answers.get("to-year")
    val toHour: Option[String] = answers.get("to-hour")
    val toMinute: Option[String] = answers.get("to-minute")

    Future.successful(
      Redirect(
        routes.HistoryController.historyByDateTime(
          fromDay,
          fromMonth,
          fromYear,
          fromHour,
          fromMinute,
          toDay,
          toMonth,
          toYear,
          toHour,
          toMinute
        )
      )
    )
  }

  private def fullOverviewTableRows(historyOverviewsFull: List[HistoryOverviewFull]): List[Seq[TableRow]] =
    historyOverviewsFull.map { historyOverviewFull =>
      val openLink = uk.gov.hmrc.eeittadminfrontend.views.html.history_link_open(historyOverviewFull._id)
      val searchByTemplateIdPage =
        uk.gov.hmrc.eeittadminfrontend.views.html.history_only_for_id(historyOverviewFull.id, historyOverviewFull._id)
      Seq(
        TableRow(
          content = HtmlContent(searchByTemplateIdPage)
        ),
        TableRow(
          content = Text(historyOverviewFull.size.toString)
        ),
        TableRow(
          content = Text(DateUtils.formatInstant(historyOverviewFull.createdAt))
        ),
        TableRow(
          content = HtmlContent(openLink)
        )
      )
    }

  def historyByFormTemplateId() = authAction.async { implicit request =>
    gformConnector.historyAllTemplateIds.map { allIds =>
      Ok(history_by_form_template_id(allIds))
    }
  }

  def diffFor1(formTemplateRawId: FormTemplateRawId, historyId1: HistoryId) = authAction.async { implicit request =>
    for {
      historyOverviews <- gformConnector.historyOverviewForTemplateId(formTemplateRawId)
      historyTemplate1 <- gformConnector.historyTemplate(historyId1)
    } yield {
      val diff: String = DiffMaker.getDiff(
        formTemplateRawId.value,
        ContentValue.JsonContent(historyTemplate1.value)
      )

      val diffHtml = uk.gov.hmrc.eeittadminfrontend.views.html.deployment_diff(Html(diff))

      val historyOverviewsNel = NonEmptyList.fromListUnsafe(historyOverviews)

      val historyComparisons: NonEmptyList[HistoryComparison] = mkDeploymentDiff(historyOverviewsNel)

      val tableRows: NonEmptyList[Seq[TableRow]] = historyComparisons.map(_.toTableRow1(formTemplateRawId, historyId1))

      val table = overviewTable(tableRows)

      val controlPanel = uk.gov.hmrc.eeittadminfrontend.views.html
        .history_control_panel(formTemplateRawId, historyId1, None, None)

      Ok(history_of(formTemplateRawId, table, controlPanel, diffHtml))

    }
  }

  def diffFor2(formTemplateRawId: FormTemplateRawId, historyId1: HistoryId, historyId2: HistoryId) = authAction.async {
    implicit request =>
      for {
        historyOverviews  <- gformConnector.historyOverviewForTemplateId(formTemplateRawId)
        historyTemplate1  <- gformConnector.historyTemplate(historyId1)
        historyTemplate2  <- gformConnector.historyTemplate(historyId2)
        previousHistoryId <- gformConnector.previousHistoryId(formTemplateRawId, historyId2)
        nextHistoryId     <- gformConnector.nextHistoryId(formTemplateRawId, historyId2)
      } yield {
        val diff: String = DiffMaker.getDiff(
          formTemplateRawId.value,
          formTemplateRawId.value,
          ContentValue.JsonContent(historyTemplate2.value),
          ContentValue.JsonContent(historyTemplate1.value),
          diffConfig.timeout
        )
        val diffHtml =
          if (diff.isEmpty) {
            uk.gov.hmrc.eeittadminfrontend.views.html.history_no_diff()
          } else
            uk.gov.hmrc.eeittadminfrontend.views.html.deployment_diff(Html(diff))

        val historyOverviewsNel = NonEmptyList.fromListUnsafe(historyOverviews)

        val historyComparisons: NonEmptyList[HistoryComparison] = mkDeploymentDiff(historyOverviewsNel)

        val tableRows: NonEmptyList[Seq[TableRow]] =
          historyComparisons.map(_.toTableRow2(formTemplateRawId, historyId1, historyId2))

        val table = overviewTable(tableRows)

        // Let not allow previous id to be newer then current id
        val restrictedPreviousId = if (previousHistoryId.contains(historyId1)) None else previousHistoryId

        val controlPanel = uk.gov.hmrc.eeittadminfrontend.views.html
          .history_control_panel(
            formTemplateRawId,
            historyId1,
            restrictedPreviousId,
            nextHistoryId
          )

        Ok(history_of(formTemplateRawId, table, controlPanel, diffHtml))
      }
  }

  private def fromDateTimeComponent(answers: Map[String, String], errors: Map[String, String]): DateInput =
    dateTimeComponent("from", "Templates created after date", answers, errors)

  private def toDateTimeComponent(answers: Map[String, String], errors: Map[String, String]): DateInput =
    dateTimeComponent("to", "Templates created before date", answers, errors)

  private def dateTimeComponent(
    prefix: String,
    legend: String,
    answers: Map[String, String],
    errors: Map[String, String]
  ): DateInput = {

    val errorMessage =
      if (errors.isEmpty) None
      else Some(ErrorMessage(content = HtmlContent(errors.values.mkString("<br>"))))

    def errorClass(id: String): String =
      if (errors.contains(id)) "govuk-input--error" else ""

    val dayId = prefix + "-day"
    val monthId = prefix + "-month"
    val yearId = prefix + "-year"
    val hourId = prefix + "-hour"
    val minuteId = prefix + "-minute"

    val items = List(
      InputItem(
        id = dayId,
        name = dayId,
        label = Some("Day"),
        value = answers.get(dayId),
        classes = s"${errorClass(dayId)} govuk-input--width-2"
      ),
      InputItem(
        id = monthId,
        name = monthId,
        label = Some("Month"),
        value = answers.get(monthId),
        classes = s"${errorClass(monthId)} govuk-input--width-2"
      ),
      InputItem(
        id = yearId,
        name = yearId,
        label = Some("Year"),
        value = answers.get(yearId),
        classes = s"${errorClass(yearId)} govuk-input--width-4"
      ),
      InputItem(
        id = hourId,
        name = hourId,
        label = Some("Hour"),
        value = answers.get(hourId),
        classes = s"${errorClass(hourId)} govuk-input--width-2"
      ),
      InputItem(
        id = minuteId,
        name = minuteId,
        label = Some("Minute"),
        value = answers.get(minuteId),
        classes = s"${errorClass(minuteId)} govuk-input--width-2"
      )
    )

    val fieldset = Fieldset(
      legend = Some(
        Legend(
          content = content.Text(legend)
        )
      )
    )
    DateInput(
      id = prefix,
      items = items.toList,
      errorMessage = errorMessage,
      fieldset = Some(fieldset)
    )
  }

  /*
   * Receives historyId and determine what historyId comes next (if any) and display the diff.
   */
  def historyDwim(formTemplateRawId: FormTemplateRawId, historyId: HistoryId) = authAction.async { implicit request =>
    gformConnector.nextHistoryId(formTemplateRawId, historyId).map { maybeHistoryId =>
      maybeHistoryId match {
        case Some(nextHistoryId) =>
          Redirect(
            routes.HistoryController
              .diffFor2(formTemplateRawId, historyId, nextHistoryId)
              .withFragment(historyId.value)
          )
        case None =>
          Redirect(
            routes.HistoryController.diffFor1(formTemplateRawId, historyId).withFragment(historyId.value)
          )
      }
    }
  }

  def historyOverviewFor(formTemplateRawId: FormTemplateRawId) = authAction.async { implicit request =>
    gformConnector.historyOverviewForTemplateId(formTemplateRawId).map { historyOverviews =>
      historyOverviews match {
        case first :: second :: _ =>
          Redirect(routes.HistoryController.diffFor2(formTemplateRawId, first._id, second._id))
        case first :: Nil =>
          Redirect(routes.HistoryController.diffFor1(formTemplateRawId, first._id))
        case _ =>
          val emptyRow = NonEmptyList.one(
            Seq(
              TableRow(
                content = Text("No history yet"),
                colspan = Some(4)
              )
            )
          )

          val table = overviewTable(emptyRow)

          Ok(history_of(formTemplateRawId, table, HtmlFormat.empty, HtmlFormat.empty))
      }
    }
  }

  def open(historyId: HistoryId) = authAction.async { implicit request =>
    gformConnector.historyTemplate(historyId).map(historyTemplate => Ok(historyTemplate.value.spaces2))
  }

  private def mkDeploymentDiff(historyOverviews: NonEmptyList[HistoryOverview]): NonEmptyList[HistoryComparison] = {
    val historyComparisons: List[HistoryComparison.Double] = historyOverviews.toList
      .sliding(2)
      .toList
      .collect { case sha1 :: sha2 :: Nil => HistoryComparison.Double(sha1, sha2) }

    val lastHistoryComparison = HistoryComparison.Single(historyOverviews.last)

    (NonEmptyList.one(lastHistoryComparison) ++ historyComparisons.reverse).reverse
  }

  private def overviewTable(
    rows: NonEmptyList[Seq[TableRow]]
  ): Table = {
    val head = Some(
      Seq(
        HeadCell(
          content = Text("Size")
        ),
        HeadCell(
          content = Text("Created at")
        ),
        HeadCell(
          content = Text("Mongo id")
        ),
        HeadCell(
          content = Text("Action")
        )
      )
    )
    Table(
      rows = rows.toList,
      head = head,
      firstCellIsHeader = false
    )
  }

  private def overviewTableFull(
    rows: List[Seq[TableRow]]
  ): Table = {
    val head = Some(
      Seq(
        HeadCell(
          content = Text("Template id")
        ),
        HeadCell(
          content = Text("Size")
        ),
        HeadCell(
          content = Text("Created at")
        ),
        HeadCell(
          content = Text("Action")
        )
      )
    )
    Table(
      rows = rows.toList,
      head = head,
      firstCellIsHeader = false
    )
  }

  private def validateDay(day: String, id: String): Validated[Map[String, String], Int] =
    if (day.matches("[0-9]+")) {
      val d = day.toInt
      if (d > 31) {
        Invalid(Map(id -> s"Day cannot be greater then 31"))
      } else {
        Valid(d)
      }
    } else Invalid(Map(id -> s"Not a valid day: $day"))

  private def validateMonth(month: String, id: String): Validated[Map[String, String], Int] =
    if (month.matches("[0-9]+")) {
      val m = month.toInt
      if (m > 12) {
        Invalid(Map(id -> s"Month cannot be greater then 12"))
      } else {
        Valid(m)
      }
    } else Invalid(Map(id -> s"Not a valid month: $month"))

  private def validateYear(year: String, id: String): Validated[Map[String, String], Int] =
    if (year.matches("[0-9]+")) {
      val y = year.toInt
      if (y < 2000) {
        Invalid(Map(id -> s"Year must be 2000 or more"))
      } else if (y > 2099) {
        Invalid(Map(id -> s"Year must less then 2099"))
      } else {
        Valid(y)
      }
    } else Invalid(Map(id -> s"Not a valid year: $year"))

  private def validateHour(hour: String, id: String): Validated[Map[String, String], Int] =
    if (hour.matches("[0-9]+")) {
      val h = hour.toInt
      if (h > 23) {
        Invalid(Map(id -> s"Hour cannot be greater then 23"))
      } else {
        Valid(h)
      }
    } else Invalid(Map(id -> s"Not a valid hour: $hour"))

  private def validateMinute(minute: String, id: String): Validated[Map[String, String], Int] =
    if (minute.matches("[0-9]+")) {
      val h = minute.toInt
      if (h > 59) {
        Invalid(Map(id -> s"Minute cannot be greater then 59"))
      } else {
        Valid(h)
      }
    } else Invalid(Map(id -> s"Not a valid minute: $minute"))

  private def validateLocalDate(
    prefix: String,
    day: Int,
    month: Int,
    year: Int
  ): Validated[Map[String, String], LocalDate] = {
    val localDate = Try(LocalDate.of(year, month, day))
    localDate.fold(error => Invalid(Map(prefix + "-day" -> error.getMessage)), localDate => Valid(localDate))
  }

  private def validateLocalDateTime(
    prefix: String,
    day: Int,
    month: Int,
    year: Int,
    hour: Int,
    minute: Int
  ): Validated[Map[String, String], LocalDateTime] = {
    val localDateTime = Try(LocalDateTime.of(year, month, day, hour, minute))
    localDateTime.fold(
      error => Invalid(Map(prefix + "-day" -> error.getMessage)),
      localDateTime => Valid(localDateTime)
    )
  }

  private def validateDateFilter(
    prefix: String,
    maybeDay: Option[String],
    maybeMonth: Option[String],
    maybeYear: Option[String],
    maybeHour: Option[String],
    maybeMinute: Option[String]
  ): Validated[Map[String, String], Option[DateFilter]] =
    (maybeDay, maybeMonth, maybeYear, maybeHour, maybeMinute) match {
      case (Some(day), Some(month), Some(year), Some(hour), Some(minute)) =>
        (
          validateDay(day, prefix + "-day"),
          validateMonth(month, prefix + "-month"),
          validateYear(year, prefix + "-year"),
          validateHour(hour, prefix + "-hour"),
          validateMinute(minute, prefix + "-minute")
        ).tupled
          .andThen { case (day, month, year, hour, minute) =>
            validateLocalDateTime(prefix, day, month, year, hour, minute)
          }
          .map(localDateTime => Some(DateFilter.DateTime(localDateTime)))

      case (Some(day), Some(month), Some(year), None, None) =>
        (
          validateDay(day, prefix + "-day"),
          validateMonth(month, prefix + "-month"),
          validateYear(year, prefix + "-year")
        ).tupled
          .andThen { case (day, month, year) =>
            validateLocalDate(prefix, day, month, year)
          }
          .map(localDate => Some(DateFilter.DateOnly(localDate)))
      case (None, None, None, None, None) => Valid(None)
      case (None, _, _, _, _)             => Invalid(Map(prefix + "-day" -> "Missing day"))
      case (_, None, _, _, _)             => Invalid(Map(prefix + "-month" -> "Missing month"))
      case (_, _, None, _, _)             => Invalid(Map(prefix + "-year" -> "Missing year"))
      case (_, _, _, None, _)             => Invalid(Map(prefix + "-hour" -> "Missing hour"))
      case (_, _, _, _, None)             => Invalid(Map(prefix + "-minute" -> "Missing minute"))
    }
}
