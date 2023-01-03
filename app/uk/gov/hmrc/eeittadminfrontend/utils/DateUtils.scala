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

package uk.gov.hmrc.eeittadminfrontend.utils

import java.time.format.DateTimeFormatter
import java.time.{ Instant, ZoneId }
import java.util.Locale
import scala.util.Try

object DateUtils {

  private def dtf(pattern: String) = DateTimeFormatter
    .ofPattern(pattern)
    .withLocale(Locale.UK)
    .withZone(ZoneId.of("Europe/London"))

  def formatInstant(instant: Instant): String = dtf("dd-MM-yyyy HH:mm:ss").format(instant)
  def formatInstantNoSpace(instant: Instant): String = dtf("dd-MM-yyyy-HH:mm:ss").format(instant)

  def formatAsInstant(string: String): String = Try(Instant.parse(string)).fold(_ => string, formatInstant)

}
