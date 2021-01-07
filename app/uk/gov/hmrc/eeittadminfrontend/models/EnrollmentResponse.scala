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

import org.slf4j.{ Logger, LoggerFactory }
import play.api.libs.json._

trait Response

case class DeltaResponse(message: String, Added: Int, changed: Int, deleted: Option[Int]) extends Response

object DeltaResponse {

  implicit val format: OFormat[DeltaResponse] = Json.format[DeltaResponse]

}

case class FailureResponse(reason: String) extends Response

object EitherResponseValueClassFormat {

  def format: Format[Either[List[ETMPBusiness], List[ETMPAgent]]] =
    new Format[Either[List[ETMPBusiness], List[ETMPAgent]]] {
      private val logger: Logger = LoggerFactory.getLogger(getClass)

      override def reads(json: JsValue) =
        json.validate[List[ETMPBusiness]] match {
          case JsSuccess(x, _) =>
            JsSuccess(Left(x))
          case JsError(error) =>
            json.validate[List[ETMPAgent]] match {
              case JsSuccess(y, _) =>
                JsSuccess(Right(y))
              case JsError(err) =>
                logger.error("Failed to return the agent")
                JsError("BOTH Agent and Business failed")
            }
        }

      override def writes(o: Either[List[ETMPBusiness], List[ETMPAgent]]) =
        o match {
          case Left(x)  => Json.toJson(x)
          case Right(y) => Json.toJson(y)
        }
    }
}

object FailureResponse {

  implicit val format: Format[FailureResponse] = Json.format[FailureResponse]
}

case class ETMPBusiness(registrationNumber: String, postcode: Option[String], countryCode: Option[String])
    extends Response

object ETMPBusiness {

  implicit val format: Format[ETMPBusiness] = Json.format[ETMPBusiness]

  implicit val eitherFormat: Format[Either[List[ETMPBusiness], List[ETMPAgent]]] = EitherResponseValueClassFormat.format

}

case class ETMPAgent(arn: String, postcode: String, countryCode: String, customers: Option[List[ETMPBusiness]])
    extends Response

object ETMPAgent {

  implicit val format: Format[ETMPAgent] = Json.format[ETMPAgent]

  val url = "agents-delta"

}

case class EnrollmentResponse(groupId: String, uid: UID, regimeId: Option[String]) extends Response

object EnrollmentResponse {

  implicit val rds: Reads[EnrollmentResponse] = new Reads[EnrollmentResponse] {
    override def reads(json: JsValue) =
      json.validate[UID] match {
        case JsSuccess(x, _) =>
          (json \ "groupId").toOption match {
            case Some(JsString(y)) =>
              (json \ "regimeId").toOption match {
                case Some(JsString(z)) =>
                  JsSuccess(EnrollmentResponse(y, x, Some(z)))
                case _ =>
                  JsSuccess(EnrollmentResponse(y, x, None))
              }
            case _ =>
              JsError("Errors")
          }
        case JsError(err) =>
          JsError("Errors")
      }
  }
}

case class UID(value: String)

object UID {

  implicit val format: Format[UID] = new Format[UID] {
    override def reads(json: JsValue) =
      (json \ "arn").toOption match {
        case Some(JsString(arn)) =>
          JsSuccess(UID(arn))
        case _ =>
          (json \ "registrationNumber").toOption match {
            case Some(JsString(registration)) =>
              JsSuccess(UID(registration))
            case _ =>
              JsError("Bob")
          }
      }

    override def writes(o: UID) =
      o match {
        case UID(x) => JsString(x)
      }
  }
}
