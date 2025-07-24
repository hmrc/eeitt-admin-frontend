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

import julienrf.json.derived
import play.api.libs.json.{ Format, OFormat }
import uk.gov.hmrc.eeittadminfrontend.controllers.AccessEnvelopeForm
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats.Implicits

import java.time.Instant
import java.util.UUID

case class CustomerDataAccessLog(
  _id: UUID,
  userName: String,
  sensitiveData: String,
  reason: String,
  envelopeIds: List[String],
  createdAt: Instant = Instant.now
) {
  def getMessage: String = {
    val common = s"Sensitive data access: User '$userName', reason '$reason', $sensitiveData"
    if (envelopeIds.size == 1) s"$common for envelopeId '${envelopeIds.head}'"
    else s"$common for list of envelopeIds '${envelopeIds.mkString(",")}'"
  }
}

object CustomerDataAccessLog {
  implicit val format: OFormat[CustomerDataAccessLog] = {
    implicit val instantFormat: Format[Instant] = Implicits.jatInstantFormat
    derived.oformat()
  }

  def apply(userName: String, sensitiveData: String, reason: String, envelopeIds: List[String]): CustomerDataAccessLog =
    CustomerDataAccessLog(UUID.randomUUID(), userName, sensitiveData, reason, envelopeIds)

  def apply(userName: String, sensitiveData: String, reason: String, envelopeId: String): CustomerDataAccessLog =
    CustomerDataAccessLog(userName, sensitiveData, reason, List(envelopeId))

  def apply(userName: String, sensitiveData: String, accessEnvelope: AccessEnvelopeForm): CustomerDataAccessLog =
    CustomerDataAccessLog(userName, sensitiveData, accessEnvelope.accessReason, accessEnvelope.envelopeId)
}
