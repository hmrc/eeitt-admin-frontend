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

package uk.gov.hmrc.eeittadminfrontend.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import uk.gov.hmrc.eeittadminfrontend.models.{ Identifier, TaxEnrolment }
import uk.gov.hmrc.eeittadminfrontend.support.WireMockSupport

trait TaxEnrolmentStubs {
  me: WireMockSupport =>

  def putes6upsertKnownFacts(identifierInput: List[Identifier], status: Int) =
    stubFor(
      put(urlEqualTo(pathEs6(identifierInput)))
        .willReturn(aResponse().withStatus(status)))

  def postEs8allocateEnrolment(groupId: String, identifierInput: List[Identifier], status: Int): Unit =
    stubFor(
      post(urlEqualTo(pathEs8(groupId, identifierInput)))
        .willReturn(aResponse().withStatus(status)))

  private def pathEs6(identifiers: List[Identifier]): String =
    s"/tax-enrolments/enrolments/${TaxEnrolment.enrolmentKey(identifiers)}"
  private def pathEs8(groupId: String, identifiers: List[Identifier]): String =
    s"/tax-enrolments/groups/$groupId/enrolments/${TaxEnrolment.enrolmentKey(identifiers)}"
}
