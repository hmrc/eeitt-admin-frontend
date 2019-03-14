package uk.gov.hmrc.eeittadminfrontend.controllers

import play.api.i18n.{DefaultLangs, DefaultMessagesApi}
import play.api.libs.json.Json
import play.api.test.{FakeHeaders, FakeRequest}
import play.api.{Configuration, Environment, Mode}
import uk.gov.hmrc.eeittadminfrontend.models.{Identifier, Verifier}
import uk.gov.hmrc.eeittadminfrontend.stubs.UserDetailsStubs
import uk.gov.hmrc.eeittadminfrontend.{AppConfig, ApplicationComponentsOnePerSuite, TestUsers, WSHttp}
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.test.UnitSpec

class DeltaControllerSpec extends UnitSpec with ApplicationComponentsOnePerSuite with UserDetailsStubs {

  override def additionalConfiguration: Map[String, Any] =
    Map( "feature.basicAuthEnabled" -> false)

  //"basicAuth.whitelist" -> "192.168.1.1",

//  val builder: AhcConfigBuilder = new AhcConfigBuilder()
//  val ahcBuilder: DefaultAsyncHttpClientConfig.Builder = builder.configure()
//  val ahcConfig: DefaultAsyncHttpClientConfig = ahcBuilder.build()
//  implicit val system: ActorSystem = ActorSystem()
//  implicit val materializer: ActorMaterializer = ActorMaterializer()
//
//  val client: AhcWSClient = new AhcWSClient(ahcConfig)
//
//  val updateEnrolmentUrl = s"http://localhost:9000/eeitt-admin-frontend/delta/enrolment/add"
//
//  def doPost(body: JsValue) = client.url(updateEnrolmentUrl).withHeaders(("token", "someToken")).post(body)

//  println("asdasdas" + applicationStart)



  val validDetailsForEnrolment = MigrationData(
    "validGroupId",
    identifiers = List(Identifier("someKey", "someValue")),
    verifiers = List(Verifier("someKey", "someValue")))

  "create enrolment for groupId" should {
    val request = FakeRequest().withJsonBody(Json.toJson(validDetailsForEnrolment))//.withHeaders(CONTENT_TYPE -> JSON)
    //.withSession("token" -> "someGoogleAuthenticationToken")

    val serverUrl = "http://test.invalid:8000"


    "200 successfully created enrolment for groupId with identifiers & verifiers provided" in {
      given202SendEmailReject(validDetailsForEnrolment.groupId)

      val a = await(deltaController.abc().apply(request))
//      println("adssadas" + a.header.headers.get("Location"))

      status(a) shouldBe 201

//      redirectLocation(a)
//            given202SendEmailReject(validDetailsForEnrolment.groupId)
//      await(doPost(Json.toJson(validDetailsForEnrolment))).status shouldBe 200
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