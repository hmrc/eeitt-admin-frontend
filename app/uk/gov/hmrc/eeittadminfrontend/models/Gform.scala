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

package uk.gov.hmrc.eeittadminfrontend.models

import cats.Order
import io.circe.{ Json => CirceJson }
import play.api.libs.json._

case class FormTemplateId(value: String) extends AnyVal {
  override def toString = value

  def formTemplateRawId = FormTemplateRawId(value)
}

object FormTemplateId {
  implicit val format: Format[FormTemplateId] = ValueClassFormatter.format(FormTemplateId.apply)(_.value)

  implicit val ordered: Order[FormTemplateId] = Order.by(_.value)
}

case class FormTemplateRawId(value: String)
object FormTemplateRawId {
  val writes: Writes[FormTemplateRawId] = Writes[FormTemplateRawId](id => JsString(id.value))
  val reads: Reads[FormTemplateRawId] = Reads[FormTemplateRawId] {
    case JsString(value) => JsSuccess(FormTemplateRawId(value))
    case otherwise       => JsError(s"Invalid formTemplateId, expected JsString, got: $otherwise")
  }

  implicit val format: Format[FormTemplateRawId] = Format[FormTemplateRawId](reads, writes)
}

case class GformId(formTemplateId: FormTemplateId)

case class GformTemplate(template: JsValue)

object GformTemplate {
  implicit val format: Format[GformTemplate] = Json.format[GformTemplate]
}

final case class FormTemplateRaw(value: CirceJson)

case class DbLookupId(_id: String)

object DbLookupId {
  implicit val format: Format[DbLookupId] = Json.format[DbLookupId]
}

case class GformServiceError(statusCode: Int, message: String) extends Exception(message)

case class FormTemplatesWithPIIInTitleForm(filters: String)
case class FormTemplateWithPIIInTitleForm(
  filters: String,
  formTemplateId: FormTemplateId
)

case class FormTemplateWithPIIInTitle(
  formTemplateId: FormTemplateId,
  piiCount: Option[Int],
  errors: List[String] = List.empty
)

case class FormTemplateWithPIIInTitleDetails(
  json: String,
  formTemplateId: FormTemplateId,
  piiDetails: List[PIIDetails]
) {
  private val piiLineNumbers = piiDetails.map(_.pos)

  def jsonAsLines = json.split("\\n").zipWithIndex.map { case (line, lineNo) =>
    (line, lineNo + 1)
  }

  def lineContainsPII(index: Int) = piiLineNumbers.find(pos => index >= pos.start && index <= pos.end)

}

case class Pos(start: Int, end: Int)
object Pos {
  implicit val format: Format[Pos] = Json.format[Pos]
}

case class PIIDetails(pos: Pos, title: String, fcIds: List[String])
object PIIDetails {
  implicit val format: Format[PIIDetails] = Json.format[PIIDetails]
}

case class PIIDetailsResponse(piis: List[PIIDetails], json: Option[String])
object PIIDetailsResponse {
  implicit val format: Format[PIIDetailsResponse] = Json.format[PIIDetailsResponse]
}
