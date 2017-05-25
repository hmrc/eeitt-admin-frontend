package uk.gov.hmrc.eeittadminfrontend.controllers

import cats.data.Validated
import org.scalatest.concurrent.ScalaFutures
import play.api.http.Status
import play.api.i18n.{DefaultLangs, DefaultMessagesApi}
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.{FakeHeaders, FakeRequest}
import play.api.{Configuration, Environment, Mode}
import uk.gov.hmrc.eeittadminfrontend._
import uk.gov.hmrc.eeittadminfrontend.connectors.EeittAdminConnector
import uk.gov.hmrc.eeittadminfrontend.controllers.auth.SecuredActionsImpl
import uk.gov.hmrc.eeittadminfrontend.models.{Email, LoginError, QueryPermission, User}
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}

class AuthControllerSpec extends UnitSpec with ApplicationComponentsOnePerSuite with ScalaFutures {

  override def additionalConfiguration: Map[String, Any] = Map("basicAuth.whitelist" -> "192.168.1.1", "feature.basicAuthEnabled" -> true)

  val serverUrl = "http://test.invalid:8000"

  "auth controller " should {

    "allow access when given a valid email" in {
      val fakeRequest = new FakeRequest("POST", "/eeittadminfrontend/login", FakeHeaders(), TestUsers.validUser(), tags = Map("CSRF_TOKEN_NAME" -> "", "CSRF_TOKEN" -> "")){
        override lazy val host: String = serverUrl
      }.withHeaders("True-Client-IP" -> "192.168.1.1")

     val result: Result = authController.checkCredentials()(fakeRequest).futureValue

      result.header.status shouldBe Status.OK
    }

    "deny access when given an invalid email" in {
      val fakeRequest = new FakeRequest("POST", "/eeittadminfrontend/login", FakeHeaders(), TestUsers.invalidUser(), tags = Map("CSRF_TOKEN_NAME" -> "", "CSRF_TOKEN" -> "")){
        override lazy val host: String = serverUrl
      }.withHeaders("True-Client-IP" -> "192.168.1.1")

      val result : Result = authController.checkCredentials()(fakeRequest).futureValue

      result.header.status shouldBe Status.UNAUTHORIZED
    }

    "register a user" in {
      val fakeRequest = new FakeRequest("POST", "/eeittadminfrontend/registration", FakeHeaders(), TestUsers.registration()){
        override lazy val host: String = serverUrl
      }.withHeaders("True-Client-IP" -> "192.168.1.1")

      val result : Result = authController.register()(fakeRequest).futureValue

      result.header.status shouldBe Status.OK
    }

    "return FORBIDDEN when whiteListing is missing" in {
      val fakeRequest = new FakeRequest("GET", "/eeittadminfrontend/login", FakeHeaders(), AnyContentAsEmpty, tags = Map("CSRF_TOKEN_NAME" -> "", "CSRF_TOKEN" -> "")){
        override lazy val host: String = serverUrl
      }

      val result : Result = authController.loginPage()(fakeRequest).futureValue

      result.header.status shouldBe Status.FORBIDDEN
    }

    "return FORBIDDEN when whiteListing is incorrect" in {
      val fakeRequest = new FakeRequest("GET", "/eeittadminfrontend/login", FakeHeaders(), AnyContentAsEmpty, tags = Map("CSRF_TOKEN_NAME" -> "", "CSRF_TOKEN" -> "")){
        override lazy val host: String = serverUrl
      }.withHeaders("True-Client-IP" -> "192.168.1.2")

      val result : Result = authController.loginPage()(fakeRequest).futureValue

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

  val authController = new AuthController(new FakeAuthConnector(), new FakeAdminConnector, securedActions)(appConfig, messageApi)
  class FakeAdminConnector extends EeittAdminConnector {
    override def checkAuth(email: Email)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Validated[LoginError, User]] = {
      if (email == Email("test@test.com")) {
        Validated.valid(User(email, Seq(QueryPermission)))
      } else {
        Validated.invalid(LoginError(List("Failed")))
      }
    }
  }
    class FakeAuthConnector extends AuthConnector {

      override val serviceUrl: String = ""

      override val http = WSHttp
  }
}