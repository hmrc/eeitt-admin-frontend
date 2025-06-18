/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.eeittadminfrontend.models.logging

import play.api.libs.json.{ Format, Json }
import uk.gov.hmrc.eeittadminfrontend.controllers.AccessEnvelopeForm

case class CustomerDataAccessLog(userName: String, sensitiveData: String, reason: String, envelopeIds: List[String])

object CustomerDataAccessLog {
  implicit val format: Format[CustomerDataAccessLog] = Json.format[CustomerDataAccessLog]

  def apply(userName: String, sensitiveData: String, reason: String, envelopeId: String): CustomerDataAccessLog =
    CustomerDataAccessLog(userName, sensitiveData, reason, List(envelopeId))

  def apply(userName: String, sensitiveData: String, accessEnvelope: AccessEnvelopeForm): CustomerDataAccessLog =
    CustomerDataAccessLog(userName, sensitiveData, accessEnvelope.accessReason, accessEnvelope.envelopeId)
}
