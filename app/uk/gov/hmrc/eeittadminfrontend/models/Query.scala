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

object Format {

  def format[A, B: Format](path: String, func : (String, B) => A, revFuncAToB : A => B, revFuncAToString: A => String): Format[A] = new Format[A] {
  override def reads(json : JsValue) = {
    json.validate match {
      case JsSuccess(x, _) =>
        (json \ path).getOrElse(JsString("ERROR")) match {
          case JsString("ERROR") =>
            JsError("Some Error")
          case JsString(y) =>
            JsSuccess(func(y, x))
        }
      case JsError(err) =>
        JsError("Some Error")
    }
  }

    override def writes(o: A) = {
      Json.obj(path -> JsString(revFuncAToString(o)))
        .++(Json.toJson(revFuncAToB(o)).as[JsObject])
    }
  }
}


object RegistrationNumber  { //BusinessUser both ETMP and Enrollments

  implicit val registrationNumberFormat: Format[RegistrationNumber] =
    Format.format[RegistrationNumber, Database]("registration", RegistrationNumber(_ , _), _.database, _.registration)

}

case class GroupId(groupid: String, userType: UserType)

object GroupId { //Enrollments only but both Agents and Business Users

  implicit val groupIdFormat: Format[GroupId] = Format.format[GroupId, UserType]("groupid", GroupId(_, _), _.userType, _.groupid)
}

case class Regime(regime: String, database: Database)

object Regime { //Business Users only ETMP and Enrollments

  implicit val regimeFormat: Format[Regime] = Format.format[Regime, Database]("regime", Regime(_, _), _.database, _.regime)
}


case class Arn(arn: String, database: Database)

object Arn {  //Agent Only ETMP and Enrollments

  implicit val format: Format[Arn] = Format.format[Arn, Database]("arn", Arn(_, _), _.database, _.arn)
}
