/*
 * Copyright 2020 HM Revenue & Customs
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

import cats.data.Validated
import cats.syntax.all._
import org.scalatest.concurrent.ScalaFutures
import play.api.http.Status
import play.api.mvc.{ AnyContentAsEmpty, Headers, Result }
import play.api.test.{ FakeHeaders, FakeRequest }
import uk.gov.hmrc.eeittadminfrontend._
import uk.gov.hmrc.eeittadminfrontend.controllers.auth.SecuredActionsImpl
import uk.gov.hmrc.eeittadminfrontend.models._
import uk.gov.hmrc.eeittadminfrontend.services.AuthService
import uk.gov.hmrc.play.bootstrap.tools.Stubs.stubMessagesControllerComponents
import uk.gov.hmrc.play.test.UnitSpec

class AuthControllerSpec extends UnitSpec with ApplicationComponentsOnePerSuite with ScalaFutures {

  override def additionalConfiguration: Map[String, Any] =
    Map("basicAuth.whitelist" -> "192.168.1.1", "feature.basicAuthEnabled" -> true)

  val serverUrl = "http://test.invalid:8000"

  "auth controller " should {

    "allow access when given a valid email" in {
      val fakeRequest = FakeRequest(
        method = "POST",
        uri = serverUrl + "/eeittadminfrontend/login",
        headers = Headers("True-Client-IP" -> "192.168.1.1"),
        body = TestUsers.validUser(),
        tags = Map("CSRF_TOKEN_NAME" -> "", "CSRF_TOKEN" -> "")
      )

      val result: Result = authController.checkCredentials()(fakeRequest).futureValue

      result.header.status shouldBe Status.SEE_OTHER
    }

    "deny access when given an invalid email" in {
      val fakeRequest = FakeRequest(
        method = "POST",
        uri = serverUrl + "/eeittadminfrontend/login",
        headers = Headers("True-Client-IP" -> "192.168.1.1"),
        body = TestUsers.invalidUser(),
        tags = Map("CSRF_TOKEN_NAME" -> "", "CSRF_TOKEN" -> "")
      )

      val result: Result = authController.checkCredentials()(fakeRequest).futureValue

      result.header.status shouldBe Status.UNAUTHORIZED
    }

    "return FORBIDDEN when whiteListing is missing" in {
      val fakeRequest = FakeRequest(
        method = "POST",
        uri = serverUrl + "/eeittadminfrontend/login",
        headers = FakeHeaders(),
        body = AnyContentAsEmpty,
        tags = Map("CSRF_TOKEN_NAME" -> "", "CSRF_TOKEN" -> ""))

      val result: Result = authController.loginPage()(fakeRequest).futureValue

      result.header.status shouldBe Status.FORBIDDEN
    }

    "return FORBIDDEN when whiteListing is incorrect" in {
      val fakeRequest = FakeRequest(
        method = "POST",
        uri = serverUrl + "/eeittadminfrontend/login",
        headers = Headers("True-Client-IP" -> "192.168.1.2"),
        body = AnyContentAsEmpty,
        tags = Map("CSRF_TOKEN_NAME" -> "", "CSRF_TOKEN" -> "")
      )

      val result: Result = authController.loginPage()(fakeRequest).futureValue

      result.header.status shouldBe Status.FORBIDDEN
    }

  }

  val securedActions = new SecuredActionsImpl(initialConfig, null)

  val authController = new AuthController(
    new FakeAuthConnector(),
    securedActions,
    new FakeAuthService,
    stubMessagesControllerComponents())(appConfig)
  class FakeAuthService extends AuthService {
    override def checkUser(email: Email): Validated[LoginError, Unit] =
      if (email.value == "test@test.com") ().valid else LoginError("Test Error").invalid
  }
}
