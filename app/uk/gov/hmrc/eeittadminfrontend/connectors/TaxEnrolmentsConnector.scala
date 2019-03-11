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

object TaxEnrolmentsConnector {

  private val sc = new ServicesConfig {
    override protected def mode = Play.current.mode
    override protected val runModeConfiguration = Play.current.configuration
  }

  lazy val taxEnrolmentsBaseUrl = s"${sc.baseUrl("tax-enrolments")}/tax-enrolments"
  private def url(identifiers: List[Identifier]): String =
    s"$taxEnrolmentsBaseUrl/enrolments/${TaxEnrolment.enrolmentKey(identifiers)}"

  // ES6
  def upsertKnownFacts(identifiers: List[Identifier], verifiers: List[Verifier])(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[HttpResponse] =
    WSHttp.PUT[List[Verifier], HttpResponse](url(identifiers), verifiers)

  // ES7
  def deleteKnownFacts(
    identifiers: List[Identifier])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    WSHttp.DELETE[HttpResponse](url(identifiers))
}