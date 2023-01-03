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

import play.api.libs.json.Format
import reactivemongo.api.bson.{ BSONHandler, Macros }
import uk.gov.hmrc.eeittadminfrontend.models.ValueClassFormatter

case class BlobSha(value: String) extends AnyVal {
  def short: String = value.take(7)
}

object BlobSha {
  implicit val handler: BSONHandler[BlobSha] = Macros.valueHandler[BlobSha]
  implicit val format: Format[BlobSha] = ValueClassFormatter.format(BlobSha.apply)(_.value)
}
