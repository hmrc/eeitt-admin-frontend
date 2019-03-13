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
//  me: WireMockSupport =>

  def given202SendEmailReject(groupId: String): Unit =
    stubFor(
      get(urlEqualTo(s"/group-identifier/$groupId"))
        .willReturn(okJson("""
                             |[
                             |{
                             |    "authProviderId": "12345-credId",
                             |    "authProviderType": "Verify",     //or "GovernmentGateway"
                             |    "name":"test",
                             |    "lastName":"test",
                             |    "dateOfBirth":"1980-06-30",
                             |    "postCode":"NW94HD",
                             |    "email":"test@test.com",
                             |    "affinityGroup" : "affinityGroup",
                             |    "agentCode" : "code",
                             |    "agentFriendlyName" : "agentFriendlyName",
                             |    "agentId": "agent-id",
                             |    "credentialRole" : "credentialRole",
                             |    "description" : "description",
                             |    "groupIdentifier" : "0203023020302302030203"
                             |},
                             |{
                             |    "authProviderId": "12346-credId",
                             |    "authProviderType": "Verify",     //or "GovernmentGateway"
                             |    "name":"test2",
                             |    "lastName":"test2",
                             |    "dateOfBirth":"1980-06-30",
                             |    "postCode":"NW97HD",
                             |    "email":"test2@test.com",
                             |    "affinityGroup" : "affinityGroup",
                             |    "agentCode" : "code",
                             |    "agentFriendlyName" : "agentFriendlyName",
                             |    "agentId": "agent-id",
                             |    "credentialRole" : "credentialRole",
                             |    "description" : "description",
                             |    "groupIdentifier" : "0203023020302302030203"
                             |}
                             |]
                         """.stripMargin)))
}
