/*
 * Copyright 2026 HM Revenue & Customs
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

import julienrf.json.derived
import play.api.libs.json.{ Format, OFormat }
import uk.gov.hmrc.eeittadminfrontend.models.FormTemplateId
import uk.gov.hmrc.eeittadminfrontend.models.fileupload.EnvelopeId

case class AsyncWorkItemData(
  id: String,
  envelopeId: EnvelopeId,
  destinationId: String,
  formTemplateId: FormTemplateId,
  submissionRef: SubmissionRef,
  uri: String,
  method: String,
  payload: String,
  username: Option[String]
)

object AsyncWorkItemData {
  implicit val envelopeIdFormat: Format[EnvelopeId] = EnvelopeId.vformat
  implicit val submissionRefFormat: Format[SubmissionRef] = SubmissionRef.vformat
  implicit val format: OFormat[AsyncWorkItemData] = derived.oformat()
}
