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

package uk.gov.hmrc.eeittadminfrontend.models.sdes

import cats.Eq
import cats.implicits.catsSyntaxEq
import play.api.libs.json.{ Format, JsError, JsResult, JsString, JsSuccess, JsValue }

sealed trait SdesConfirmationType

object SdesConfirmationType {

  case object Mark extends SdesConfirmationType
  case object Resend extends SdesConfirmationType

  implicit val format: Format[SdesConfirmationType] = new Format[SdesConfirmationType] {
    override def writes(o: SdesConfirmationType): JsValue = o match {
      case Mark   => JsString("Mark")
      case Resend => JsString("Resend")
    }

    override def reads(json: JsValue): JsResult[SdesConfirmationType] =
      json match {
        case JsString("Mark")   => JsSuccess(Mark)
        case JsString("Resend") => JsSuccess(Resend)
        case JsString(err) =>
          JsError(s"only for valid Delete or Resend.$err is not allowed")
        case _ => JsError("Failure")
      }
  }

  val all: Set[SdesConfirmationType] = Set(Mark, Resend)

  def unapply(s: String): Option[SdesConfirmationType] = all.find(_.toString === s)

  def fromName(confirmationType: SdesConfirmationType): String = confirmationType match {
    case Mark   => "Mark"
    case Resend => "Resend"
  }

  implicit val catsEq: Eq[SdesConfirmationType] = Eq.fromUniversalEquals
}
