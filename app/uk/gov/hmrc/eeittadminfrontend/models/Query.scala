/*
 * Copyright 2020 HM Revenue & Customs
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

case class RegistrationNumber(registration: String, database: Database)

object ValueClassFormat {

  def format[A, B: Format](
    path: String,
    func: (String, B) => A,
    revFuncAToB: A => B,
    revFuncAToString: A => String): Format[A] = new Format[A] {
    override def reads(json: JsValue) =
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

    override def writes(o: A) =
      Json
        .obj(path -> JsString(revFuncAToString(o)))
        .++(Json.toJson(revFuncAToB(o)).as[JsObject])
  }
}

object RegistrationNumber { //BusinessUser both ETMP and Enrollments

  implicit val registrationNumberFormat: Format[RegistrationNumber] =
    ValueClassFormat
      .format[RegistrationNumber, Database]("registration", RegistrationNumber(_, _), _.database, _.registration)

  implicit val eitherFormat = EitherValueClassFormat.format

}

case class GroupId(groupid: String, userType: UserType)

object GroupId { //Enrollments only but both Agents and Business Users

  implicit val groupIdFormat: Format[GroupId] =
    ValueClassFormat.format[GroupId, UserType]("groupid", GroupId(_, _), _.userType, _.groupid)
}

case class Regime(regime: String, database: Database)

object Regime { //Business Users only ETMP and Enrollments

  implicit val regimeFormat: Format[Regime] =
    ValueClassFormat.format[Regime, Database]("regime", Regime(_, _), _.database, _.regime)
}

case class Arn(arn: String, database: Database)

object Arn { //Agent Only ETMP and Enrollments

  implicit val format: Format[Arn] = ValueClassFormat.format[Arn, Database]("arn", Arn(_, _), _.database, _.arn)

}

object EitherValueClassFormat {

  def format: Format[Either[Arn, RegistrationNumber]] = new Format[Either[Arn, RegistrationNumber]] {
    override def reads(json: JsValue) =
      json.validate[Arn] match {
        case JsSuccess(x, _) =>
          JsSuccess(Left(x))
        case JsError(error) =>
          json.validate[RegistrationNumber] match {
            case JsSuccess(y, _) =>
              JsSuccess(Right(y))
            case JsError(err) =>
              JsError("BOTH Agent and Business failed")
          }
      }

    override def writes(o: Either[Arn, RegistrationNumber]) =
      o match {
        case Left(x)  => Json.toJson(x)
        case Right(y) => Json.toJson(y)
      }
  }
}

object ValueClassFormatter {
  def format[A: Format](fromStringToA: String => A)(fromAToString: A => String) =
    Format[A](
      Reads[A] {
        case JsString(str) => JsSuccess(fromStringToA(str))
        case unknown       => JsError(s"JsString value expected, got: $unknown")
      },
      Writes[A](a => JsString(fromAToString(a)))
    )
}
