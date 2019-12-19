/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.eeittadminfrontend.models.fileupload

import play.api.libs.json.{ Json, Reads }

case class Envelope(
  id: EnvelopeId,
  metadata: Metadata,
  constraints: Constraints,
  status: String,
  destination: String,
  application: String,
  files: List[File]
) {
  def size = files.map(_.length).sum
}

object Envelope {
  def nonExistentEnvelope(id: EnvelopeId) = Envelope(
    id,
    Metadata("-", "-"),
    Constraints(0, 0, 0),
    "-",
    "-",
    "-",
    List.empty
  )
  implicit val reads: Reads[Envelope] = Json.reads[Envelope]
}
