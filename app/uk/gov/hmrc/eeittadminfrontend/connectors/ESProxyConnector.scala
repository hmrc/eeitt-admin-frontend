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
import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse }
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.{ ExecutionContext, Future }

object ESProxyConnector {

  private val sc = new ServicesConfig {
    override protected def mode = Play.current.mode
    override protected val runModeConfiguration = Play.current.configuration
  }

  lazy val esProxyBaseUrl = s"${sc.baseUrl("enrolment-store-proxy")}/enrolment-store-proxy/enrolment-store"

  private def knownFactsUrl(identifiers: List[Identifier]): String =
    s"$esProxyBaseUrl/enrolments/${TaxEnrolment.enrolmentKey(identifiers)}"

  private def groupUrl(groupId: String): String =
    s"$esProxyBaseUrl/enrolments/$groupId/enrolments"

  private def enrolmentUrl(groupId: String, identifiers: List[Identifier]) =
    s"${groupUrl(groupId)}/${TaxEnrolment.enrolmentKey(identifiers)}"

  // ES3
  def queryEnrolments(groupId: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    WSHttp.GET(groupUrl(groupId))

  // ES6
  def upsertKnownFacts(identifiers: List[Identifier], verifiers: List[Verifier])(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[HttpResponse] =
    WSHttp.PUT[Verifiers, HttpResponse](knownFactsUrl(identifiers), Verifiers(verifiers))

  // ES7
  def deleteKnownFacts(
    identifiers: List[Identifier])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    WSHttp.DELETE[HttpResponse](knownFactsUrl(identifiers))

  // ES8
  def addEnrolment(groupId: String, userId: String, identifiers: List[Identifier], verifiers: List[Verifier])(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[HttpResponse] =
    WSHttp
      .POST(enrolmentUrl(groupId, identifiers), TaxEnrolmentPayload(verifiers, "principal", userId, "gform-enrolment"))

  // ES9
  def deallocateEnrolment(groupId: String, identifiers: List[Identifier])(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[HttpResponse] =
    WSHttp.DELETE(enrolmentUrl(groupId, identifiers))

  // ES20
  def queryKnownFacts(knownFacts: List[KnownFact])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[JsValue] =
    WSHttp
      .POST[ServiceQuery, JsValue](s"$esProxyBaseUrl/enrolments", ServiceQuery(TaxEnrolment.service, knownFacts))
}
