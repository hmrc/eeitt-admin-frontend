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

import java.time.Instant
import play.api.libs.json.{ Format, Json }
import uk.gov.hmrc.eeittadminfrontend.models.FormTemplateRawId

final case class HistoryOverview(
  _id: HistoryId,
  createdAt: Instant,
  size: Int
)

object HistoryOverview {
  implicit val format: Format[HistoryOverview] = Json.format
}

final case class HistoryOverviewFull(
  _id: HistoryId,
  id: FormTemplateRawId,
  createdAt: Instant,
  size: Int
)

object HistoryOverviewFull {
  implicit val format: Format[HistoryOverviewFull] = Json.format
}
