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

package uk.gov.hmrc.eeittadminfrontend.models.sdes

import play.api.libs.json.{ Json, OFormat }
import uk.gov.hmrc.eeittadminfrontend.models.fileupload.EnvelopeId

import java.time.Instant

case class SdesReportsPageData(
  sdesSubmissions: List[SdesReportData],
  count: Long,
  countAll: Long
)

object SdesReportsPageData {
  implicit val format: OFormat[SdesReportsPageData] = Json.format[SdesReportsPageData]
}

case class SdesReportData(
  consolidatorJobId: String,
  startTimestamp: Instant,
  endTimestamp: Instant,
  correlationId: CorrelationId,
  envelopeId: EnvelopeId,
  submissionRef: SubmissionRef,
  submittedAt: Option[Instant],
  status: NotificationStatus,
  failureReason: String,
  lastUpdated: Option[Instant]
)

object SdesReportData {
  implicit val format: OFormat[SdesReportData] = Json.format[SdesReportData]
}
