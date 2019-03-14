package uk.gov.hmrc.eeittadminfrontend.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import uk.gov.hmrc.eeittadminfrontend.support.WireMockSupport

trait UserDetailsStubs {
    me: WireMockSupport =>

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