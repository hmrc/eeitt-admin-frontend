/*
 * Copyright 2019 HM Revenue & Customs
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

import play.api.Play
import play.api.libs.json.JsValue
import uk.gov.hmrc.eeittadminfrontend.WSHttp
import uk.gov.hmrc.eeittadminfrontend.models._
import uk.gov.hmrc.http.{ FailedDependencyException, HeaderCarrier, HttpResponse }
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.{ ExecutionContext, Future }

object EnrolmentStoreProxyConnector {

  private val sc = new ServicesConfig {
    override protected def mode = Play.current.mode
    override protected val runModeConfiguration = Play.current.configuration
  }

  lazy val enrolmentStoreProxyBaseUrl = s"${sc.baseUrl("enrolment-store-proxy")}/enrolment-store-proxy/enrolment-store"
  private def url(identifiers: List[Identifier]): String =
    s"$enrolmentStoreProxyBaseUrl/enrolments/${TaxEnrolment.enrolmentKey(identifiers)}"

  // ES3
  def queryEnrolments(groupId: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[JsValue]] =
    WSHttp.GET(s"$enrolmentStoreProxyBaseUrl/groups/$groupId/enrolments").map {
      case response if response.status == 200 => Some(response.json)
      case response if response.status == 204 => None
    }

  // ES6
  def upsertKnownFacts(identifiers: List[Identifier], verifiers: List[Verifier])(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[HttpResponse] =
    WSHttp
      .PUT[Verifiers, HttpResponse](url(identifiers), Verifiers(verifiers))
      .recover {
        case e =>
          throw new FailedDependencyException("adding known facts has failed, it is a requirement for an enrolment")
      }

  // ES7
  def deleteKnownFacts(
    identifiers: List[Identifier])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    WSHttp.DELETE[HttpResponse](url(identifiers))

  // ES8
  def addEnrolment(groupId: String, userId: String, identifiers: List[Identifier], verifiers: List[Verifier])(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[HttpResponse] =
    WSHttp
      .POST(
        s"$enrolmentStoreProxyBaseUrl/groups/$groupId/enrolments/${TaxEnrolment.enrolmentKey(identifiers)}",
        TaxEnrolmentPayload(verifiers, "principal", userId, "gform-enrolment")
      )

  // ES9
  def deallocateEnrolment(groupId: String, identifiers: List[Identifier])(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[HttpResponse] =
    WSHttp.DELETE(
      s"$enrolmentStoreProxyBaseUrl/groups/$groupId/enrolments/${TaxEnrolment.enrolmentKey(identifiers)}"
    )

  // ES20
  def queryKnownFacts(knownFacts: List[KnownFact])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[JsValue] =
    WSHttp.POST[ServiceQuery, JsValue](
      s"$enrolmentStoreProxyBaseUrl/enrolments",
      ServiceQuery(TaxEnrolment.service, knownFacts))
}
