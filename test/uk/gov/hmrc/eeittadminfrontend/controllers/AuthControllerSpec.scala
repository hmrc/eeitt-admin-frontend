/*
 * Copyright 2017 HM Revenue & Customs
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
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import org.scalatest.concurrent.ScalaFutures
import play.api.http.Status
import play.api.i18n.{ DefaultLangs, DefaultMessagesApi }
import play.api.mvc.{ Action, AnyContent, AnyContentAsEmpty, Result }
import play.api.test.{ FakeHeaders, FakeRequest }
import play.api.{ Configuration, Environment, Mode }
import uk.gov.hmrc.eeittadminfrontend._
import uk.gov.hmrc.eeittadminfrontend.controllers.auth.SecuredActionsImpl
import uk.gov.hmrc.eeittadminfrontend.models._
import uk.gov.hmrc.eeittadminfrontend.services.{ AuthService, GoogleVerifier, GoogleVerifierHelper }
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec
import cats.data._
import cats.syntax.all._

import scala.concurrent.{ ExecutionContext, Future }

class AuthControllerSpec extends UnitSpec with ApplicationComponentsOnePerSuite with ScalaFutures {

  override def additionalConfiguration: Map[String, Any] = Map("basicAuth.whitelist" -> "192.168.1.1", "feature.basicAuthEnabled" -> true)

  val serverUrl = "http://test.invalid:8000"

  "auth controller " should {

    "allow access when given a valid email" in {
      val fakeRequest = new FakeRequest("POST", "/eeittadminfrontend/login", FakeHeaders(), TestUsers.validUser(), tags = Map("CSRF_TOKEN_NAME" -> "", "CSRF_TOKEN" -> "")) {
        override lazy val host: String = serverUrl
      }.withHeaders("True-Client-IP" -> "192.168.1.1")

      val result: Result = authController.checkCredentials()(fakeRequest).futureValue

      result.header.status shouldBe Status.SEE_OTHER
    }

    "deny access when given an invalid email" in {
      val fakeRequest = new FakeRequest("POST", "/eeittadminfrontend/login", FakeHeaders(), TestUsers.invalidUser(), tags = Map("CSRF_TOKEN_NAME" -> "", "CSRF_TOKEN" -> "")) {
        override lazy val host: String = serverUrl
      }.withHeaders("True-Client-IP" -> "192.168.1.1")

      val result: Result = authController.checkCredentials()(fakeRequest).futureValue

      result.header.status shouldBe Status.UNAUTHORIZED
    }

    "return FORBIDDEN when whiteListing is missing" in {
      val fakeRequest = new FakeRequest("GET", "/eeittadminfrontend/login", FakeHeaders(), AnyContentAsEmpty, tags = Map("CSRF_TOKEN_NAME" -> "", "CSRF_TOKEN" -> "")) {
        override lazy val host: String = serverUrl
      }

      val result: Result = authController.loginPage()(fakeRequest).futureValue

      result.header.status shouldBe Status.FORBIDDEN
    }

    "return FORBIDDEN when whiteListing is incorrect" in {
      val fakeRequest = new FakeRequest("GET", "/eeittadminfrontend/login", FakeHeaders(), AnyContentAsEmpty, tags = Map("CSRF_TOKEN_NAME" -> "", "CSRF_TOKEN" -> "")) {
        override lazy val host: String = serverUrl
      }.withHeaders("True-Client-IP" -> "192.168.1.2")

      val result: Result = authController.loginPage()(fakeRequest).futureValue

      result.header.status shouldBe Status.FORBIDDEN
    }

  }

  val securedActions = new SecuredActionsImpl(fakeApplication.configuration, null)

  val appConfig = new AppConfig {
    val analyticsToken: String = ""
    val analyticsHost: String = ""
    val reportAProblemPartialUrl: String = ""
    val reportAProblemNonJSUrl: String = ""
  }

  val configuration: Configuration = Configuration.reference
  val mode: Mode.Mode = Mode.Test
  val env: Environment = Environment.simple(mode = mode)
  val langs = new DefaultLangs(configuration)
  val messageApi = new DefaultMessagesApi(env, configuration, langs)

  val authController = new AuthController(new FakeAuthConnector(), securedActions, new FakeAuthService, new FakeGoogleVerifier)(appConfig, messageApi)

  class FakeAuthService extends AuthService {
    override def checkUser(email: Email): Validated[LoginError, Unit] = {
      if (email.value == "test@test.com") ().valid else LoginError("Test Error").invalid
    }
  }

  class FakeGoogleVerifier extends GoogleVerifier {

    override def apply(string: String) = {
      if (string == "test@test.com") {
        "test@test.com"
      } else {
        "invalidtest@test.com"
      }
    }
  }

  class FakeAuthConnector extends AuthConnector {

    override val serviceUrl: String = ""

    override val http = WSHttp
  }
}