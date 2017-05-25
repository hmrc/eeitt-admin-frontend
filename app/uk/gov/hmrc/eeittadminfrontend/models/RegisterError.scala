/*
 * Copyright 2017 HM Revenue & Customs
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

import cats.data.Validated
import play.api.libs.json._

case class RegisterError(error : List[String])

object RegisterError {

  implicit val format: OFormat[RegisterError] = Json.format[RegisterError]

  implicit val readsRegisterErrorString : Reads[Validated[RegisterError, String]] = new Reads[Validated[RegisterError, String]] {
    override def reads(json: JsValue): JsResult[Validated[RegisterError, String]] = {
      json.validateOpt[String] match {
        case JsSuccess(Some(x), _) => JsSuccess(Validated.valid(x))
        case JsError(err) =>
          json.validateOpt[RegisterError] match {
            case JsSuccess(Some(y), _) => JsSuccess(Validated.invalid(y))
            case JsError(error) => JsError(error.++(err))
          }
      }
    }
  }
}
