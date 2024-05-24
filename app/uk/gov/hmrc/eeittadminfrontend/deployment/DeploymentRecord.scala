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

package uk.gov.hmrc.eeittadminfrontend.deployment

import julienrf.json.derived
import play.api.libs.json.{ Format, OFormat }

import java.time.Instant
import uk.gov.hmrc.eeittadminfrontend.models.{ FormTemplateId, Username }
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

case class DeploymentRecord(
  username: Username,
  createdAt: Instant,
  filename: Filename,
  formTemplateId: FormTemplateId,
  blobSha: BlobSha,
  commitSha: CommitSha
)

object DeploymentRecord {
  implicit val format: OFormat[DeploymentRecord] = {
    implicit val dtf: Format[Instant] = MongoJavatimeFormats.instantFormat
    derived.oformat()
  }
}
