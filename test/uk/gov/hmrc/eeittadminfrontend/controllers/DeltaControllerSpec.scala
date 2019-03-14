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

import play.api.i18n.{ DefaultLangs, DefaultMessagesApi }
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.{ Configuration, Environment, Mode }
import uk.gov.hmrc.eeittadminfrontend.models.{ Identifier, Verifier }
import uk.gov.hmrc.eeittadminfrontend.stubs.{ TaxEnrolmentStubs, UserDetailsStubs }
import uk.gov.hmrc.eeittadminfrontend.{ AppConfig, ApplicationComponentsOnePerSuite, WSHttp }
import uk.gov.hmrc.http.BadRequestException
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.test.UnitSpec

class DeltaControllerSpec
    extends UnitSpec with ApplicationComponentsOnePerSuite with UserDetailsStubs with TaxEnrolmentStubs {

  override def additionalConfiguration: Map[String, Any] =
    Map(
      "microservice.services.user-details.port"   -> wireMockPort, //TODO the ports should be different everytime wireMockPort is called...
      "microservice.services.tax-enrolments.port" -> wireMockPort
    )

  val validDetailsForEnrolment = MigrationData(
    "validGroupId",
    identifiers = List(Identifier("someKey", "someValue")),
    verifiers = List(Verifier("someKey", "someValue")))

  "create enrolment for groupId" should {

    "200 successfully created enrolment for groupId with identifiers & verifiers provided" in {
      val request = FakeRequest()
        .withJsonBody(Json.toJson(validDetailsForEnrolment))
        .withSession("token" -> "someGoogleAuthenticationToken")

      givenUserDetailsWithResponseBody(validDetailsForEnrolment.groupId)
      putes6upsertKnownFacts(validDetailsForEnrolment.identifiers, 204)
      postEs8allocateEnrolment(validDetailsForEnrolment.groupId, validDetailsForEnrolment.identifiers, 201)

      val result = await(deltaController.abc().apply(request))
      status(result) shouldBe 200
    }

    "303 authentication token is missing" in {
      val request = FakeRequest().withJsonBody(Json.toJson(validDetailsForEnrolment)) //"token" in session is missing

      val result = await(deltaController.abc().apply(request))
      status(result) shouldBe 303
      result.header.headers.get("Location") shouldBe Some("/eeitt-admin-frontend/login")
    }

    "400 body is not a valid json" in {
      val badBody = "anything that is not a valid json that matches the model"
      val request =
        FakeRequest().withJsonBody(Json.toJson(badBody)).withSession("token" -> "someGoogleAuthenticationToken")

      an[BadRequestException] shouldBe thrownBy(await(deltaController.abc().apply(request)))
    }

    "500 when userDetails returns 404" in {
      val request = FakeRequest()
        .withJsonBody(Json.toJson(validDetailsForEnrolment))
        .withSession("token" -> "someGoogleAuthenticationToken")

      givenUserDetails(validDetailsForEnrolment.groupId, 404)

      val result = await(deltaController.abc().apply(request))
      status(result) shouldBe 500
    }

    "500 when userDetails returns 400" in {
      val request = FakeRequest()
        .withJsonBody(Json.toJson(validDetailsForEnrolment))
        .withSession("token" -> "someGoogleAuthenticationToken")

      givenUserDetails(validDetailsForEnrolment.groupId, 400)

      val result = await(deltaController.abc().apply(request))
      status(result) shouldBe 500
    }

    "500 when userDetails returns 500" in {
      val request = FakeRequest()
        .withJsonBody(Json.toJson(validDetailsForEnrolment))
        .withSession("token" -> "someGoogleAuthenticationToken")

      givenUserDetails(validDetailsForEnrolment.groupId, 500)

      val result = await(deltaController.abc().apply(request))
      status(result) shouldBe 500
    }

    "500 when tax-enrolments es6 returns 400" in {
      val request = FakeRequest()
        .withJsonBody(Json.toJson(validDetailsForEnrolment))
        .withSession("token" -> "someGoogleAuthenticationToken")

      givenUserDetailsWithResponseBody(validDetailsForEnrolment.groupId)
      putes6upsertKnownFacts(validDetailsForEnrolment.identifiers, 400)
      postEs8allocateEnrolment(validDetailsForEnrolment.groupId, validDetailsForEnrolment.identifiers, 201)

      val result = await(deltaController.abc().apply(request))
      status(result) shouldBe 500
    }

    "500 when tax-enrolments es6 returns 404" in {
      val request = FakeRequest()
        .withJsonBody(Json.toJson(validDetailsForEnrolment))
        .withSession("token" -> "someGoogleAuthenticationToken")

      givenUserDetailsWithResponseBody(validDetailsForEnrolment.groupId)
      putes6upsertKnownFacts(validDetailsForEnrolment.identifiers, 404)
      postEs8allocateEnrolment(validDetailsForEnrolment.groupId, validDetailsForEnrolment.identifiers, 201)

      val result = await(deltaController.abc().apply(request))
      status(result) shouldBe 500
    }

    "500 when tax-enrolments es6 returns 500" in {
      val request = FakeRequest()
        .withJsonBody(Json.toJson(validDetailsForEnrolment))
        .withSession("token" -> "someGoogleAuthenticationToken")

      givenUserDetailsWithResponseBody(validDetailsForEnrolment.groupId)
      putes6upsertKnownFacts(validDetailsForEnrolment.identifiers, 500)
      postEs8allocateEnrolment(validDetailsForEnrolment.groupId, validDetailsForEnrolment.identifiers, 201)

      val result = await(deltaController.abc().apply(request))
      status(result) shouldBe 500
    }

    "500 when tax-enrolments es8 returns 400" in {
      val request = FakeRequest()
        .withJsonBody(Json.toJson(validDetailsForEnrolment))
        .withSession("token" -> "someGoogleAuthenticationToken")

      givenUserDetailsWithResponseBody(validDetailsForEnrolment.groupId)
      putes6upsertKnownFacts(validDetailsForEnrolment.identifiers, 204)
      postEs8allocateEnrolment(validDetailsForEnrolment.groupId, validDetailsForEnrolment.identifiers, 400)

      val result = await(deltaController.abc().apply(request))
      status(result) shouldBe 500
    }

    "500 when tax-enrolments es8 returns 404" in {
      val request = FakeRequest()
        .withJsonBody(Json.toJson(validDetailsForEnrolment))
        .withSession("token" -> "someGoogleAuthenticationToken")

      givenUserDetailsWithResponseBody(validDetailsForEnrolment.groupId)
      putes6upsertKnownFacts(validDetailsForEnrolment.identifiers, 204)
      postEs8allocateEnrolment(validDetailsForEnrolment.groupId, validDetailsForEnrolment.identifiers, 404)

      val result = await(deltaController.abc().apply(request))
      status(result) shouldBe 500
    }

    "500 when tax-enrolments es8 returns 500" in {
      val request = FakeRequest()
        .withJsonBody(Json.toJson(validDetailsForEnrolment))
        .withSession("token" -> "someGoogleAuthenticationToken")

      givenUserDetailsWithResponseBody(validDetailsForEnrolment.groupId)
      putes6upsertKnownFacts(validDetailsForEnrolment.identifiers, 204)
      postEs8allocateEnrolment(validDetailsForEnrolment.groupId, validDetailsForEnrolment.identifiers, 500)

      val result = await(deltaController.abc().apply(request))
      status(result) shouldBe 500
    }
  }

  val configuration: Configuration = Configuration.reference
  val mode: Mode.Mode = Mode.Test
  val env: Environment = Environment.simple(mode = mode)
  val langs = new DefaultLangs(configuration)

  implicit val messageApi = new DefaultMessagesApi(env, configuration, langs)
  implicit val appConfig = new AppConfig {
    val analyticsToken: String = ""
    val analyticsHost: String = ""
    val reportAProblemPartialUrl: String = ""
    val reportAProblemNonJSUrl: String = ""
  }

  class FakeAuthConnector extends AuthConnector {
    override val serviceUrl: String = ""
    override val http = WSHttp
  }

  val deltaController = new DeltaController(new FakeAuthConnector)
}
