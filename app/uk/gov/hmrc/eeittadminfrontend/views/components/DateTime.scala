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

package uk.gov.hmrc.eeittadminfrontend.views.components

import uk.gov.hmrc.govukfrontend.views.Aliases.{ ErrorMessage, Fieldset, HtmlContent, InputItem, Legend }
import uk.gov.hmrc.govukfrontend.views.viewmodels.dateinput.DateInput
import uk.gov.hmrc.govukfrontend.views.viewmodels.content
object DateTime {

  private def createInputItem(
    id: String,
    label: String,
    width: Int,
    answers: Map[String, String],
    errors: Map[String, String]
  ): InputItem =
    InputItem(
      id = id,
      name = id,
      label = Some(label),
      value = answers.get(id),
      classes = s"${errorClass(id, errors)} govuk-input--width-$width"
    )

  private def errorClass(id: String, errors: Map[String, String]): String =
    if (errors.contains(id)) "govuk-input--error" else ""

  private def createItems(
    prefix: String,
    fields: List[(String, String, Int)],
    answers: Map[String, String],
    errors: Map[String, String]
  ): List[InputItem] =
    fields.map { case (id, label, width) => createInputItem(s"$prefix-$id", label, width, answers, errors) }

  private def createErrorMessage(errors: Map[String, String]): Option[ErrorMessage] =
    if (errors.isEmpty) None else Some(ErrorMessage(content = HtmlContent(errors.values.mkString("<br>"))))

  private def createFieldset(legend: String): Fieldset =
    Fieldset(legend = Some(Legend(content = content.Text(legend))))

  private def createDateInput(
    prefix: String,
    legend: String,
    fields: List[(String, String, Int)],
    answers: Map[String, String],
    errors: Map[String, String]
  ): DateInput = {
    val items = createItems(prefix, fields, answers, errors)
    val errorMessage = createErrorMessage(errors)
    val fieldset = createFieldset(legend)
    DateInput(id = prefix, items = items, errorMessage = errorMessage, fieldset = Some(fieldset))
  }

  def dateTimeComponent(
    prefix: String,
    legend: String,
    answers: Map[String, String],
    errors: Map[String, String]
  ): DateInput =
    createDateInput(
      prefix,
      legend,
      List(("day", "Day", 2), ("month", "Month", 2), ("year", "Year", 4), ("hour", "Hour", 2), ("minute", "Minute", 2)),
      answers,
      errors
    )

  def dateComponent(
    prefix: String,
    legend: String,
    answers: Map[String, String],
    errors: Map[String, String]
  ): DateInput =
    createDateInput(
      prefix,
      legend,
      List(("day", "Day", 2), ("month", "Month", 2), ("year", "Year", 4)),
      answers,
      errors
    )
}
