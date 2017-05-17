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

import play.api.libs.json._
import play.api.Logger

case class RegistrationNumber(registration: String, database: Database)

object RegistrationNumber  { //BusinessUser both ETMP and Enrollments

  implicit val registrationNumberFormat: Reads[RegistrationNumber] = Json.format[RegistrationNumber]
}

case class GroupId(groupid: String, userType: UserType)

object GroupId { //Enrollments only but both Agents and Business Users

  implicit val groupIdFormat: Reads[GroupId] = Json.format[GroupId]
}

case class Regime(regime: String, database: Database)

case class Arn(arn: String, database: Database)

trait UserType

object UserType {

  implicit val format: Format[UserType] = new Format[UserType] {
    override def reads(json: JsValue): JsResult[UserType] = {
      (json \ "user").getOrElse(JsString("Error")) match {
        case JsString("Agent") => JsSuccess(Agent)
        case JsString("Business") => JsSuccess(Business)
        case _ => JsError("Bob")
      }
    }

    override def writes(o: UserType): JsString = {
      o match {
        case Agent => JsString("Agent")
        case Business => JsString("Business")
        case _ =>
          Logger.error("illegal arguement")
          JsString("Error")
      }
    }
  }
}

object Agent extends UserType
object Business extends UserType
object Arn {  //Agent Only ETMP and Enrollments

  implicit val format: OFormat[Arn] = Json.format[Arn]
}

object Regime { //Business Users only ETMP and Enrollments

  implicit val regimeFormat: Reads[Regime] = Json.format[Regime]
}