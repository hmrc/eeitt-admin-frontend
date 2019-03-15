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

  def get200Es3queryEnrolments(groupId: String): Unit =
    stubFor(
      get(urlEqualTo(pathEs3(groupId)))
        .willReturn(okJson("""{
                             |    "service":"IR-SA",
                             |    "enrolmentDate":"2018-10-05 14:42:00",
                             |    "status":"Activated",
                             |    "friendlyName":"Onizukas Self Assessment",
                             |    "identifiers": [
                             |          {
                             |            "key": "UTR",
                             |            "value": "1713501890"
                             |          }
                             |    ]
                             |}""".stripMargin)))

  def getEs3queryEnrolments(groupId: String, status: Int) =
    stubFor(
      get(urlEqualTo(pathEs3(groupId)))
        .willReturn(aResponse().withStatus(status)))

  def deleteEs7DeallocateEnrolment(identifiers: List[Identifier], status: Int) =
    stubFor(
      delete(urlEqualTo(pathEs6Es7(identifiers)))
        .willReturn(aResponse().withStatus(status)))

  def putEs6upsertKnownFacts(identifierInput: List[Identifier], status: Int) =
    stubFor(
      put(urlEqualTo(pathEs6Es7(identifierInput)))
        .willReturn(aResponse().withStatus(status)))

  def postEs8allocateEnrolment(groupId: String, identifierInput: List[Identifier], status: Int): Unit =
    stubFor(
      post(urlEqualTo(pathEs8Es9(groupId, identifierInput)))
        .willReturn(aResponse().withStatus(status)))

  def postEs9deallocateEnrolment(groupId: String, identifierInput: List[Identifier], status: Int): Unit =
    stubFor(
      delete(urlEqualTo(pathEs8Es9(groupId, identifierInput)))
        .willReturn(aResponse().withStatus(status)))

  private def pathEs3(groupId: String): String =
    s"/enrolment-store-proxy/enrolment-store/groups/$groupId/enrolments"

  private def pathEs6Es7(identifiers: List[Identifier]): String =
    s"/enrolment-store-proxy/enrolment-store/enrolments/${TaxEnrolment.enrolmentKey(identifiers)}"

  private def pathEs8Es9(groupId: String, identifiers: List[Identifier]): String =
    s"/enrolment-store-proxy/enrolment-store/groups/$groupId/enrolments/${TaxEnrolment.enrolmentKey(identifiers)}"
}
