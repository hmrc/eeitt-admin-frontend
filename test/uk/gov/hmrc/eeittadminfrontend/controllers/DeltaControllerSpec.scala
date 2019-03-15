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

package uk.gov.hmrc.eeittadminfrontend.controllers

import play.api.libs.json.{ JsArray, Json }
import play.api.mvc.Call
import play.api.test.FakeRequest
import uk.gov.hmrc.eeittadminfrontend.ApplicationComponentsOnePerSuite
import uk.gov.hmrc.eeittadminfrontend.models._
import uk.gov.hmrc.eeittadminfrontend.stubs.{ EnrolmentStoreProxyStubs, UserDetailsStubs }
import uk.gov.hmrc.http.BadRequestException
import uk.gov.hmrc.play.test.UnitSpec

class DeltaControllerSpec
    extends UnitSpec with ApplicationComponentsOnePerSuite with UserDetailsStubs with EnrolmentStoreProxyStubs {

  override def additionalConfiguration: Map[String, Any] =
    Map(
      "microservice.services.user-details.port"          -> wireMockPort,
      "microservice.services.enrolment-store-proxy.port" -> wireMockPort
    )

  val deltaController = new DeltaController(new FakeAuthConnector)

  val validGroupId = "validGroupId"
  val identifiers = List(Identifier("someKey", "someValue"))
  val verifiers = List(Verifier("someKey", "someValue"))

  val validMigrationData = MigrationData(validGroupId, identifiers, verifiers)

  "upsertKnownFacts" should {
    val validData: String = Json.toJson(UpsertRequest(identifiers, verifiers)).toString()

    "200 successfully upserts knownFacts" in {
      val request = FakeRequest()
        .withSession("token" -> "someGoogleAuthenticationToken")
        .withFormUrlEncodedBody(("identifiersverifiers", validData))
        .copyFakeRequest(tags = Map("CSRF_TOKEN_NAME" -> "", "CSRF_TOKEN" -> ""))

      putEs6upsertKnownFacts(identifiers, 204)

      val result = deltaController.upsertKnownFacts()(request)

      status(result) shouldBe 200
    }

    "303 unauthorised" in {
      val request = FakeRequest()
        .withFormUrlEncodedBody(("csrfToken", "someCsrfToken"), ("identifiers", Json.toJson(identifiers).toString()))

      val result = await(deltaController.upsertKnownFacts()(request))
      status(result) shouldBe 303
      result.header.headers.get("Location") shouldBe Some("/eeitt-admin-frontend/login")
    }

    "400 bad json/body in request" in {
      val request = FakeRequest()
        .withSession("token" -> "someGoogleAuthenticationToken")
        .withFormUrlEncodedBody(("identifiersverifiers", "someBadValue"))
        .copyFakeRequest(tags = Map("CSRF_TOKEN_NAME" -> "", "CSRF_TOKEN" -> ""))

      val result = deltaController.upsertKnownFacts()(request)

      status(result) shouldBe 400
    }

    "500 when enrolment-store-proxy returns 400" in {
      val request = FakeRequest()
        .withSession("token" -> "someGoogleAuthenticationToken")
        .withFormUrlEncodedBody(("identifiersverifiers", validData))
        .copyFakeRequest(tags = Map("CSRF_TOKEN_NAME" -> "", "CSRF_TOKEN" -> ""))

      putEs6upsertKnownFacts(identifiers, 400)

      val result = deltaController.upsertKnownFacts()(request)

      status(result) shouldBe 500
    }
  }

  "deleteKnownFacts" should {
    "200 successfully remove knownFacts" in {
      val request = FakeRequest()
        .withSession("token" -> "someGoogleAuthenticationToken")
        .withFormUrlEncodedBody(("identifiers", Json.toJson(identifiers).toString()))
        .copyFakeRequest(tags = Map("CSRF_TOKEN_NAME" -> "", "CSRF_TOKEN" -> ""))

      deleteEs7DeallocateEnrolment(identifiers, 204)

      val result = await(deltaController.deleteKnownFacts()(request))
      status(result) shouldBe 200
    }

    "303 unauthorised" in {
      val request = FakeRequest()
        .withFormUrlEncodedBody(("csrfToken", "someCsrfToken"), ("identifiers", Json.toJson(identifiers).toString()))

      val result = await(deltaController.deleteKnownFacts()(request))
      status(result) shouldBe 303
      result.header.headers.get("Location") shouldBe Some("/eeitt-admin-frontend/login")
    }

    "400 bad input" in {
      val request = FakeRequest()
        .withSession("token"                          -> "someGoogleAuthenticationToken")
        .copyFakeRequest(tags = Map("CSRF_TOKEN_NAME" -> "", "CSRF_TOKEN" -> ""))

      val result = await(deltaController.deleteKnownFacts()(request))
      status(result) shouldBe 400
    }

    "500 request to delete knownFacts fails due to 404" in {
      val request = FakeRequest()
        .withSession("token" -> "someGoogleAuthenticationToken")
        .withFormUrlEncodedBody(("identifiers", Json.toJson(identifiers).toString()))
        .copyFakeRequest(tags = Map("CSRF_TOKEN_NAME" -> "", "CSRF_TOKEN" -> ""))

      deleteEs7DeallocateEnrolment(identifiers, 404)

      val result = await(deltaController.deleteKnownFacts()(request))
      status(result) shouldBe 500
    }

    "500 request to delete knownFacts fails due to 500" in {
      val request = FakeRequest()
        .withSession("token" -> "someGoogleAuthenticationToken")
        .withFormUrlEncodedBody(("identifiers", Json.toJson(identifiers).toString()))
        .copyFakeRequest(tags = Map("CSRF_TOKEN_NAME" -> "", "CSRF_TOKEN" -> ""))

      deleteEs7DeallocateEnrolment(identifiers, 500)

      val result = await(deltaController.deleteKnownFacts()(request))
      status(result) shouldBe 500
    }
  }

  "deleteEnrolment" should {
    "200 successfully deallocate enrolment" in {
      val request = FakeRequest()
        .withJsonBody(Json.toJson(validMigrationData))
        .withSession("token" -> "someGoogleAuthenticationToken")

      postEs9deallocateEnrolment(validGroupId, identifiers, 204)

      val result = await(deltaController.deleteEnrolment()(request))
      status(result) shouldBe 200
    }

    "303 unauthorised" in {
      val request = FakeRequest()
        .withJsonBody(Json.toJson(validMigrationData))

      val result = await(deltaController.deleteEnrolment()(request))
      status(result) shouldBe 303
      result.header.headers.get("Location") shouldBe Some("/eeitt-admin-frontend/login")
    }

    "500 fail when enrolment-store-proxy returns 404 deallocate enrolment" in {
      val request = FakeRequest()
        .withJsonBody(Json.toJson(validMigrationData))
        .withSession("token" -> "someGoogleAuthenticationToken")

      postEs9deallocateEnrolment(validGroupId, identifiers, 404)

      val result = await(deltaController.deleteEnrolment()(request))
      status(result) shouldBe 500
    }

    "500 fail when enrolment-store-proxy returns 500" in {
      val request = FakeRequest()
        .withJsonBody(Json.toJson(validMigrationData))
        .withSession("token" -> "someGoogleAuthenticationToken")

      postEs9deallocateEnrolment(validGroupId, identifiers, 500)

      val result = await(deltaController.deleteEnrolment()(request))
      status(result) shouldBe 500
    }
  }

  "addEnrol get prerequisite data from user-details & assign enrolment" should {
    "200 successfully assign enrolment" in {
      val request = FakeRequest()
        .withJsonBody(Json.toJson(validMigrationData))
        .withSession("token" -> "someGoogleAuthenticationToken")

      givenUserDetailsWithResponseBody(validGroupId)
      postEs8allocateEnrolment(validGroupId, identifiers, 201)

      val result = await(deltaController.addEnrol()(request))
      status(result) shouldBe 200
    }

    "303 user unauthorised" in {
      val request = FakeRequest().withJsonBody(Json.toJson(validMigrationData))

      val result = await(deltaController.addEnrol()(request))
      status(result) shouldBe 303
      result.header.headers.get("Location") shouldBe Some("/eeitt-admin-frontend/login")
    }

    "500 when user-details returns 404 failing to obtain user-details" in {
      val request = FakeRequest()
        .withJsonBody(Json.toJson(validMigrationData))
        .withSession("token" -> "someGoogleAuthenticationToken")

      givenUserDetails(validGroupId, 404)

      val result = await(deltaController.addEnrol()(request))
      status(result) shouldBe 500
    }

    "500 when user-details returns 500 failing to obtain user-details" in {
      val request = FakeRequest()
        .withJsonBody(Json.toJson(validMigrationData))
        .withSession("token" -> "someGoogleAuthenticationToken")

      givenUserDetails(validGroupId, 500)

      val result = await(deltaController.addEnrol()(request))
      status(result) shouldBe 500
    }

    "500 when enrolment-store-proxy returns 404" in {
      val request = FakeRequest()
        .withJsonBody(Json.toJson(validMigrationData))
        .withSession("token" -> "someGoogleAuthenticationToken")

      givenUserDetailsWithResponseBody(validGroupId)
      postEs8allocateEnrolment(validGroupId, identifiers, 404)

      val result = await(deltaController.addEnrol()(request))
      status(result) shouldBe 500
    }

    "500 when enrolment-store-proxy returns 500" in {
      val request = FakeRequest()
        .withJsonBody(Json.toJson(validMigrationData))
        .withSession("token" -> "someGoogleAuthenticationToken")

      givenUserDetailsWithResponseBody(validGroupId)
      postEs8allocateEnrolment(validGroupId, identifiers, 500)

      val result = await(deltaController.addEnrol()(request))
      status(result) shouldBe 500
    }
  }

  "addFactsEnrol get prerequisite data from user-details, es6 add known-facts, es8 allocate enrolment" should {

    "200 successfully created enrolment for groupId with identifiers & verifiers provided" in {
      val request = FakeRequest()
        .withJsonBody(Json.toJson(validMigrationData))
        .withSession("token" -> "someGoogleAuthenticationToken")

      givenUserDetailsWithResponseBody(validGroupId)
      putEs6upsertKnownFacts(identifiers, 204)
      postEs8allocateEnrolment(validGroupId, identifiers, 201)

      val result = await(deltaController.addFactsEnrol()(request))
      status(result) shouldBe 200
    }

    "303 authentication token is missing" in {
      val request = FakeRequest().withJsonBody(Json.toJson(validMigrationData)) //"token" in session is missing

      val result = await(deltaController.addFactsEnrol()(request))
      status(result) shouldBe 303
      result.header.headers.get("Location") shouldBe Some("/eeitt-admin-frontend/login")
    }

    "400 body is not a valid json" in {
      val badBody = "anything that is not a valid json that matches the model"
      val request =
        FakeRequest().withJsonBody(Json.toJson(badBody)).withSession("token" -> "someGoogleAuthenticationToken")

      an[BadRequestException] shouldBe thrownBy(await(deltaController.addFactsEnrol()(request)))
    }

    "500 when userDetails returns 404" in {
      val request = FakeRequest()
        .withJsonBody(Json.toJson(validMigrationData))
        .withSession("token" -> "someGoogleAuthenticationToken")

      givenUserDetails(validGroupId, 404)

      val result = await(deltaController.addFactsEnrol()(request))
      status(result) shouldBe 500
    }

    "500 when userDetails returns 400" in {
      val request = FakeRequest()
        .withJsonBody(Json.toJson(validMigrationData))
        .withSession("token" -> "someGoogleAuthenticationToken")

      givenUserDetails(validGroupId, 400)

      val result = await(deltaController.addFactsEnrol()(request))
      status(result) shouldBe 500
    }

    "500 when userDetails returns 500" in {
      val request = FakeRequest()
        .withJsonBody(Json.toJson(validMigrationData))
        .withSession("token" -> "someGoogleAuthenticationToken")

      givenUserDetails(validGroupId, 500)

      val result = await(deltaController.addFactsEnrol()(request))
      status(result) shouldBe 500
    }

    "500 when enrolment-store-proxy es6 returns 400" in {
      val request = FakeRequest()
        .withJsonBody(Json.toJson(validMigrationData))
        .withSession("token" -> "someGoogleAuthenticationToken")

      givenUserDetailsWithResponseBody(validGroupId)
      putEs6upsertKnownFacts(identifiers, 400)
      postEs8allocateEnrolment(validGroupId, identifiers, 201)

      val result = await(deltaController.addFactsEnrol()(request))
      status(result) shouldBe 500
    }

    "500 when enrolment-store-proxy es6 returns 404" in {
      val request = FakeRequest()
        .withJsonBody(Json.toJson(validMigrationData))
        .withSession("token" -> "someGoogleAuthenticationToken")

      givenUserDetailsWithResponseBody(validGroupId)
      putEs6upsertKnownFacts(identifiers, 404)
      postEs8allocateEnrolment(validGroupId, identifiers, 201)

      val result = await(deltaController.addFactsEnrol()(request))
      status(result) shouldBe 500
    }

    "500 when enrolment-store-proxy es6 returns 500" in {
      val request = FakeRequest()
        .withJsonBody(Json.toJson(validMigrationData))
        .withSession("token" -> "someGoogleAuthenticationToken")

      givenUserDetailsWithResponseBody(validGroupId)
      putEs6upsertKnownFacts(identifiers, 500)
      postEs8allocateEnrolment(validGroupId, identifiers, 201)

      val result = await(deltaController.addFactsEnrol()(request))
      status(result) shouldBe 500
    }

    "500 when enrolment-store-proxy es8 returns 400" in {
      val request = FakeRequest()
        .withJsonBody(Json.toJson(validMigrationData))
        .withSession("token" -> "someGoogleAuthenticationToken")

      givenUserDetailsWithResponseBody(validGroupId)
      putEs6upsertKnownFacts(identifiers, 204)
      postEs8allocateEnrolment(validGroupId, identifiers, 400)

      val result = await(deltaController.addFactsEnrol()(request))
      status(result) shouldBe 500
    }

    "500 when enrolment-store-proxy es8 returns 404" in {
      val request = FakeRequest()
        .withJsonBody(Json.toJson(validMigrationData))
        .withSession("token" -> "someGoogleAuthenticationToken")

      givenUserDetailsWithResponseBody(validGroupId)
      putEs6upsertKnownFacts(identifiers, 204)
      postEs8allocateEnrolment(validGroupId, identifiers, 404)

      val result = await(deltaController.addFactsEnrol()(request))
      status(result) shouldBe 500
    }

    "500 when enrolment-store-proxy es8 returns 500" in {
      val request = FakeRequest()
        .withJsonBody(Json.toJson(validMigrationData))
        .withSession("token" -> "someGoogleAuthenticationToken")

      givenUserDetailsWithResponseBody(validGroupId)
      putEs6upsertKnownFacts(identifiers, 204)
      postEs8allocateEnrolment(validGroupId, identifiers, 500)

      val result = await(deltaController.addFactsEnrol()(request))
      status(result) shouldBe 500
    }
  }

  def testAuthorised(action: Call): Unit = {}
}
