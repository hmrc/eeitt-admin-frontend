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

import play.api.Logger
import play.api.libs.json._

trait UserType {
  val url : String
}

object Agent extends UserType {

  override def toString() = {
    "Enrollment Agent "
  }

  override val url: String = "/agent-by-gid/"
}
object Business extends UserType {

  override def toString() = {
    "Enrollment Business "
  }

  override val url: String = "/business-by-gid/"
}

object UserType {

  implicit val format: Format[UserType] = new Format[UserType] {
    override def reads(json: JsValue): JsResult[UserType] = {
      (json \ "user").getOrElse(JsString("Error")) match {
        case JsString("Agent") => JsSuccess(Agent)
        case JsString("Business") => JsSuccess(Business)
        case _ => JsError("Bob")
      }
    }

    override def writes(o: UserType): JsValue = {
      o match {
        case Agent => Json.obj("user" -> "Agent")
        case Business => Json.obj("user" -> "Business")
        case _ =>
          Logger.error("illegal arguement")
          JsString("Error")
      }
    }
  }
}