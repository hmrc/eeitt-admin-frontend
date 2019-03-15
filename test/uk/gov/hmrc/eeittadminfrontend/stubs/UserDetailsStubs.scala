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
import uk.gov.hmrc.eeittadminfrontend.support.WireMockSupport

trait UserDetailsStubs {
  me: WireMockSupport =>

  def givenUserDetailsWithResponseBody(groupId: String): Unit =
    stubFor(
      get(urlEqualTo(s"/group-identifier/$groupId"))
        .willReturn(okJson("""[
                      {"gatewayId":"3494280737743238",
                      "authProviderId":"3494280737743238",
                      "authProviderType":"GovernmentGateway",
                      "name":"firstName",
                      "email":"someEmail@email.com",
                      "affinityGroup":"Organisation",
                      "credentialRole":"User",
                      "groupIdentifier":"5822AFAC-B986-423B-B6B5-2FBC5A8627BE"}]
                           """.stripMargin)))

  def givenUserDetails(groupId: String, status: Int): Unit =
    stubFor(
      get(urlEqualTo(s"/group-identifier/$groupId"))
        .willReturn(aResponse().withStatus(status)))
}
