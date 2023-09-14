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

package uk.gov.hmrc.eeittadminfrontend.history

import cats.syntax.all._
import play.twirl.api.Html
import uk.gov.hmrc.eeittadminfrontend.models.FormTemplateRawId
import uk.gov.hmrc.eeittadminfrontend.utils.DateUtils
import uk.gov.hmrc.govukfrontend.views.html.components.{ HtmlContent, TableRow, Text }

sealed trait HistoryComparison extends Product with Serializable {
  def fold[B](f: HistoryComparison.Single => B)(g: HistoryComparison.Double => B): B =
    this match {
      case d: HistoryComparison.Single => f(d)
      case d: HistoryComparison.Double => g(d)
    }

  def toTableRow1(
    formTemplateRawId: FormTemplateRawId,
    highlightPlus: HistoryId
  ): Seq[TableRow] =
    fold(singleHistory(formTemplateRawId, highlightPlus, "highlightPlus"))(
      doubleHistory(formTemplateRawId, HistoryId(""), HistoryId(""))
    )

  def toTableRow2(
    formTemplateRawId: FormTemplateRawId,
    highlightPlus: HistoryId,
    highlightMinus: HistoryId
  ): Seq[TableRow] =
    fold(singleHistory(formTemplateRawId, highlightMinus, "highlightMinus"))(
      doubleHistory(formTemplateRawId, highlightPlus, highlightMinus)
    )

  private def singleHistory(formTemplateRawId: FormTemplateRawId, historyId: HistoryId, highlightClassName: String)(
    single: HistoryComparison.Single
  ): Seq[TableRow] = {
    val historyRecord = single.ho

    val noDiffLink = uk.gov.hmrc.eeittadminfrontend.views.html.history_link_diff1(formTemplateRawId, historyRecord._id)

    val rows = commonColumns(historyRecord, noDiffLink, openLink(historyId), historyId)

    rows.map(
      _.copy(classes =
        if (historyId === historyRecord._id) highlightClassName
        else ""
      )
    )
  }

  private def doubleHistory(formTemplateRawId: FormTemplateRawId, highlightPlus: HistoryId, highlightMinus: HistoryId)(
    hd: HistoryComparison.Double
  ): Seq[TableRow] = {
    val sha1 = hd.ho1._id
    val sha2 = hd.ho2._id

    val historyRecord = hd.ho1

    val diffLink = uk.gov.hmrc.eeittadminfrontend.views.html.history_link_diff2(formTemplateRawId, sha1, sha2)

    val rows = commonColumns(historyRecord, diffLink, openLink(sha1), sha1)

    rows.map(
      _.copy(classes =
        if (highlightPlus === sha1) "highlightPlus"
        else if (highlightMinus === sha1) "highlightMinus"
        else ""
      )
    )
  }

  private def openLink(historyId: HistoryId): Html =
    uk.gov.hmrc.eeittadminfrontend.views.html.history_link_open(historyId)

  private def commonColumns(
    historyOverview: HistoryOverview,
    diffLink: Html,
    openLink: Html,
    historyId: HistoryId
  ): Seq[TableRow] = Seq(
    TableRow(
      attributes = Map("id" -> historyId.value), // For html fragments (#) to work
      content = Text(historyOverview.size.toString)
    ),
    TableRow(
      content = Text(DateUtils.formatInstant(historyOverview.createdAt))
    ),
    TableRow(
      content = HtmlContent(diffLink)
    ),
    TableRow(
      content = HtmlContent(openLink)
    )
  )
}

object HistoryComparison {
  case class Single(ho: HistoryOverview) extends HistoryComparison
  case class Double(ho1: HistoryOverview, ho2: HistoryOverview) extends HistoryComparison

}
