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

package uk.gov.hmrc.eeittadminfrontend.models

import cats.Order
import play.api.libs.json._
import reactivemongo.api.bson.{ BSONHandler, Macros }
import uk.gov.hmrc.eeittadminfrontend.deployment.Filename

case class FormTemplateId(value: String) extends AnyVal {
  override def toString = value
}

object FormTemplateId {
  implicit val handler: BSONHandler[FormTemplateId] = Macros.valueHandler[FormTemplateId]

  implicit val format: Format[FormTemplateId] = ValueClassFormatter.format(FormTemplateId.apply)(_.value)

  implicit val ordered: Order[FormTemplateId] = Order.by(_.value)
}

case class GformId(formTemplateId: FormTemplateId)

case class GformTemplate(template: JsValue)

object GformTemplate {
  implicit val format: Format[GformTemplate] = Json.format[GformTemplate]
}

case class DbLookupId(_id: String)

object DbLookupId {
  implicit val format: Format[DbLookupId] = Json.format[DbLookupId]
}

case class GformServiceError(statusCode: Int, message: String) extends Exception(message)

case class FormTemplatesWithPIIInTitleForm(filters: String)
case class FormTemplateWithPIIInTitleForm(
  filters: String,
  formTemplateId: FormTemplateId,
  templateSource: TemplateSource
)

case class FormTemplateWithPIIInTitle(
  fileName: Filename,
  formTemplateId: FormTemplateId,
  githubPIICount: Option[Int],
  mongoPIICount: Option[Int],
  errors: List[String] = List.empty
)

case class FormTemplateWithPIIInTitleDetails(
  fileName: Filename,
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

sealed trait TemplateSource {
  override def toString = this match {
    case Github => "github"
    case Mongo  => "mongo"
  }
}
case object Github extends TemplateSource
case object Mongo extends TemplateSource

object TemplateSource {

  def fromString(value: String): TemplateSource = value match {
    case "github" => Github
    case "mongo"  => Mongo
    case other    => throw new IllegalArgumentException(s"'$other' is not a valid TemplateSource")
  }
}

case class ErrorResponse(error: String, status: Option[Int] = None)
