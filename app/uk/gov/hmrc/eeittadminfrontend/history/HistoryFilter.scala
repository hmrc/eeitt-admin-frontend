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

import java.time.{ LocalDate, LocalDateTime }
import julienrf.json.derived
import play.api.libs.json.{ Format, Json, OFormat }

import java.time.format.DateTimeFormatter

sealed trait DateFilter extends Product with Serializable

object DateFilter {
  final case class DateOnly(localDate: LocalDate) extends DateFilter
  final case class DateTime(localDateTime: LocalDateTime) extends DateFilter {
    override def toString: String = localDateTime.format(DateTimeFormatter.ofPattern("h:mma dd MMM yyyy"))
  }

  implicit val format: OFormat[DateFilter] = derived.oformat()
}

final case class HistoryFilter(
  from: Option[DateFilter],
  to: Option[DateFilter]
)

object HistoryFilter {
  val empty: HistoryFilter = HistoryFilter(None, None)
  implicit val format: Format[HistoryFilter] = Json.format[HistoryFilter]
}
