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

import cats.Eq
import play.api.libs.json.{ Format, JsError, JsString, JsSuccess, Reads, Writes }

final case class HistoryId(value: String) extends AnyVal

object HistoryId {

  implicit val equal: Eq[HistoryId] = Eq.fromUniversalEquals

  val writes: Writes[HistoryId] = Writes[HistoryId](id => JsString(id.value))
  val reads: Reads[HistoryId] = Reads[HistoryId] {
    case JsString(value) => JsSuccess(HistoryId(value))
    case otherwise       => JsError(s"Invalid historyId, expected JsString, got: $otherwise")
  }

  implicit val format: Format[HistoryId] = Format[HistoryId](reads, writes)
}
