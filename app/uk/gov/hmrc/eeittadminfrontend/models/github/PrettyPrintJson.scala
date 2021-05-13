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

package uk.gov.hmrc.eeittadminfrontend.models.github

import io.circe._
import java.nio.charset.StandardCharsets
import play.api.libs.json.{ Format, Json }

case class PrettyPrintJson(
  content: Array[Byte]
)

object PrettyPrintJson {

  private val postmanPrinter = Printer.spaces4.copy(colonLeft = "", lrbracketsEmpty = "")

  def apply(template: Json): PrettyPrintJson =
    PrettyPrintJson(postmanPrinter.print(template).getBytes(StandardCharsets.UTF_8))

  implicit val format: Format[PrettyPrintJson] = Json.format[PrettyPrintJson]
}
