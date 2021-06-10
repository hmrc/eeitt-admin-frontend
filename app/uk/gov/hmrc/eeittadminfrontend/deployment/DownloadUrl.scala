/*
 * Copyright 2021 HM Revenue & Customs
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

import cats.syntax.all._
import org.http4s.{ ParseResult, Uri }
import play.api.libs.json.Format
import uk.gov.hmrc.eeittadminfrontend.models.ValueClassFormatter

case class DownloadUrl(uri: Uri) extends AnyVal

object DownloadUrl {
  def stripQueryString(url: String): ParseResult[DownloadUrl] =
    Uri.fromString(url).map(uri => DownloadUrl(uri.removeQueryParam("token")))
  implicit val format: Format[DownloadUrl] =
    ValueClassFormatter
      .formatE[DownloadUrl](url => Uri.fromString(url).bimap(_.message, DownloadUrl(_)))(_.uri.renderString)
}
