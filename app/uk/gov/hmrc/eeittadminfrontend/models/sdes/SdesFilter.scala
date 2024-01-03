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

import play.api.libs.json.{ Format, Json }
import uk.gov.hmrc.eeittadminfrontend.history.DateFilter
import uk.gov.hmrc.eeittadminfrontend.models.FormTemplateId
import uk.gov.hmrc.eeittadminfrontend.models.fileupload.EnvelopeId

final case class SdesFilter(
  page: Int,
  pageSize: Int,
  isProcessed: Option[Boolean],
  envelopeId: Option[EnvelopeId],
  formTemplateId: Option[FormTemplateId],
  status: Option[NotificationStatus],
  destination: Option[SdesDestination],
  beforeCreatedAt: Option[Boolean],
  from: Option[DateFilter],
  to: Option[DateFilter]
)

object SdesFilter {
  implicit val format: Format[SdesFilter] = Json.format[SdesFilter]
  def empty(page: Int, pageSize: Int) = SdesFilter(page, pageSize, None, None, None, None, None, None, None, None)
}
