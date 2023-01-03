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

import play.api.libs.json._

object ValueClassFormatter {
  def format[A: Format](fromStringToA: String => A)(fromAToString: A => String) =
    Format[A](
      Reads[A] {
        case JsString(str) => JsSuccess(fromStringToA(str))
        case unknown       => JsError(s"JsString value expected, got: $unknown")
      },
      Writes[A](a => JsString(fromAToString(a)))
    )

  def formatE[A: Format](fromString: String => Either[String, A])(fromAToString: A => String) =
    Format[A](
      Reads[A] {
        case JsString(str) => fromString(str).fold(JsError(_), JsSuccess(_))
        case unknown       => JsError(s"JsString value expected, got: $unknown")
      },
      Writes[A](a => JsString(fromAToString(a)))
    )
}
