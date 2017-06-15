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

package uk.gov.hmrc.eeittadminfrontend.connectors

import play.api.Logger
import play.api.libs.json.{JsString, JsValue, Json, OFormat}
import play.api.libs.ws.WSRequest
import play.api.mvc.Request
import uk.gov.hmrc.eeittadminfrontend.WSHttp
import uk.gov.hmrc.eeittadminfrontend.controllers.User
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpPost, HttpPut}

import scala.concurrent.{ExecutionContext, Future}

class EMACConnector extends EMACConnectorHelper

case class KeyValuePair(key: String, value: String)

object KeyValuePair {

  implicit val format = Json.format[KeyValuePair]
}
case class EnrollmentKey(service : String, identifier: String, value: String)
case class KnownFacts(enrollmentKey: EnrollmentKey, verifiers: List[KeyValuePair])
case class Enrollment(user: String, enrollmentKey: EnrollmentKey, verifiers: List[KeyValuePair])

trait EMACConnectorHelper {

  val PUT : HttpPut = WSHttp
  val POST : HttpPost = WSHttp

  //ES6
  def loadKF(knownFacts: KnownFacts)(implicit hc: HeaderCarrier, ec: ExecutionContext, request : Request[Map[String, Seq[String]]]) = {
    val json = Json.parse(
      s"""{
        |"verifiers" : [
        |${knownFacts.verifiers}
        |]
        |}""".stripMargin
    )
    PUT.PUT[JsValue, JsValue](s"http://enrolment-store-proxy.protected.mdtp:80/enrolment-store-proxy/enrolment-store/enrolments/${knownFacts.enrollmentKey.service}~${knownFacts.enrollmentKey.identifier}~${knownFacts.enrollmentKey.value}", json)
  }

  //ES8
  def allocateAnEnrollment(enrollment: Enrollment, user: User)(implicit hc: HeaderCarrier, ec: ExecutionContext, request : Request[Map[String, Seq[String]]]): Future[Option[JsValue]] = {
    val allocateInsertJson: JsValue =
      Json.parse(s"""
         |{
         |    "friendlyName": "friendly name",
         |    "type": "principal",
         |    "verifiers": [
         |       ${enrollment.verifiers}
         |    ]
         |}
      """.stripMargin)

    POST.POST[JsValue, Option[JsValue]](s"http://enrolment-store-proxy.protected.mdtp:80/enrolment-store-proxy/enrolment-store/groups/${user.groupId}/enrolments/${enrollment.enrollmentKey.service}~${enrollment.enrollmentKey.identifier}~${enrollment.enrollmentKey.value}", allocateInsertJson, Seq("Content-Type" -> "application/json"))
  }

  //ES11
  def assignEnrollment(enrollment: Enrollment, user: User)(implicit hc: HeaderCarrier, ec: ExecutionContext, request : Request[Map[String, Seq[String]]]) = {
    allocateAnEnrollment(enrollment, user).map {
      case None => {
        POST.POSTEmpty[Option[JsValue]](s"http://enrolment-store-proxy.protected.mdtp:80/enrolment-store-proxy/enrolment-store/users/${user.credId}/enrolments/${enrollment.enrollmentKey.value}")
      }
      case Some(x) =>
        Logger.error("EMAC Connector returned an error.")
        Future.successful(Some(JsString("Failed with this error" + x)))
    }
  }

  // Requires the Delete endPoints.
}