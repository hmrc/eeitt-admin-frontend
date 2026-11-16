/*
 * Copyright 2026 HM Revenue & Customs
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

import com.github.tomakehurst.wiremock.client.WireMock.{ aResponse, get, getRequestedFor, stubFor, urlEqualTo, verify }
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.SystemMaterializer
import munit.FunSuite
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import play.api.test.{ FakeRequest, Helpers }
import play.api.test.Helpers._
import uk.gov.hmrc.eeittadminfrontend.common.WiremockSupport
import uk.gov.hmrc.eeittadminfrontend.connectors.GformConnector
import uk.gov.hmrc.eeittadminfrontend.models.FormTemplateId
import uk.gov.hmrc.eeittadminfrontend.services.{ BatchUploadService, GformService }
import uk.gov.hmrc.eeittadminfrontend.validators.FormTemplateValidator
import uk.gov.hmrc.http.test.HttpClientV2Support
import uk.gov.hmrc.internalauth.client.{ FrontendAuthComponents, Predicate, Retrieval }
import uk.gov.hmrc.internalauth.client.test.{ FrontendAuthComponentsStub, StubBehaviour }
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.concurrent.duration._

class GformsControllerSpec extends FunSuite with MockitoSugar with WiremockSupport with HttpClientV2Support {

  implicit private val ec: ExecutionContext = ExecutionContext.global
  private val actorSystem = ActorSystem("GformsControllerSpec")
  implicit private val materializer: Materializer = SystemMaterializer(actorSystem).materializer
  implicit private val controllerComponents: ControllerComponents = Helpers.stubControllerComponents()
  private val formTemplateId = FormTemplateId("test-form")
  private val internalTemplate = Json.obj("formTemplateId" -> formTemplateId.value, "field" -> "value")
  private val requestExternalUrl = s"/gform/formtemplates/${formTemplateId.value}/sensitive"
  private val requestInternalUrl = s"/gform/formtemplates/${formTemplateId.value}/internal"

  private val gformConnector = new GformConnector(
    mkHttpClientV2(),
    new ServicesConfig(
      Configuration(
        "microservice.services.gform.host" -> "localhost",
        "microservice.services.gform.port" -> wiremockPort.toString
      )
    )
  )

  private val stubBehaviour = new StubBehaviour {
    override def stubAuth[R](predicate: Option[Predicate], retrieval: Retrieval[R]): Future[R] =
      Future.successful(Retrieval.Username("test-user").asInstanceOf[R])
  }
  private val frontendAuthComponents: FrontendAuthComponents =
    FrontendAuthComponentsStub(stubBehaviour)

  private val controller = new GformsController(
    frontendAuthComponents,
    gformConnector,
    mock[GformService],
    mock[FormTemplateValidator],
    mock[BatchUploadService],
    Helpers.stubMessagesControllerComponents(),
    mock[uk.gov.hmrc.eeittadminfrontend.views.html.gform_formtemplate_pii],
    mock[uk.gov.hmrc.eeittadminfrontend.views.html.gform_formtemplates_pii],
    mock[uk.gov.hmrc.eeittadminfrontend.views.html.gform_formtemplates_pii_home],
    mock[uk.gov.hmrc.eeittadminfrontend.views.html.gform_page],
    mock[uk.gov.hmrc.eeittadminfrontend.views.html.batch_upload],
    mock[uk.gov.hmrc.eeittadminfrontend.views.html.handlebars_template],
    mock[uk.gov.hmrc.eeittadminfrontend.views.html.confirmation]
  )

  override def beforeAll(): Unit =
    startServer()

  override def afterAll(): Unit = {
    stopServer()
    val _ = Await.result(actorSystem.terminate(), 5.seconds)
  }

  test("getInternalGformByFormType returns the internal JSON for the submitted form template ID") {
    stubFor(
      get(urlEqualTo(requestInternalUrl))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(Json.stringify(internalTemplate))
        )
    )

    val result = controller.getInternalGformByFormType.apply(
      FakeRequest(POST, "/get-internal-gform")
        .withSession("authToken" -> "test-token")
        .withFormUrlEncodedBody("formTemplateId.value" -> formTemplateId.value)
    )

    assertEquals(status(result), OK)
    assertEquals(contentAsString(result), Json.prettyPrint(internalTemplate))
    verify(getRequestedFor(urlEqualTo(requestInternalUrl)))
  }

  test("getInternalGformByFormType preserves the internal template error message when the lookup fails") {
    stubFor(
      get(urlEqualTo(requestInternalUrl))
        .willReturn(aResponse().withStatus(404).withBody("template not found"))
    )

    val result = controller.getInternalGformByFormType.apply(
      FakeRequest(POST, "/get-internal-gform")
        .withSession("authToken" -> "test-token")
        .withFormUrlEncodedBody("formTemplateId.value" -> formTemplateId.value)
    )

    assertEquals(status(result), OK)
    assertEquals(
      contentAsString(result),
      s"Problem when fetching internal form template: $formTemplateId. Reason: template not found"
    )
  }

  test("getGformByFormType returns the template JSON from the sensitive endpoint") {
    val template = Json.obj("formTemplateId" -> formTemplateId.value, "representation" -> "sensitive")
    stubFor(
      get(urlEqualTo(requestExternalUrl))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(Json.stringify(template))
        )
    )

    val result = controller.getGformByFormType.apply(
      FakeRequest(POST, "/get-gform")
        .withSession("authToken" -> "test-token")
        .withFormUrlEncodedBody("formTemplateId.value" -> formTemplateId.value)
    )

    assertEquals(status(result), OK)
    assertEquals(contentAsString(result), Json.prettyPrint(template))
    verify(getRequestedFor(urlEqualTo(requestExternalUrl)))
  }

  test("getGformByFormType preserves the template error message when the lookup fails") {
    stubFor(
      get(urlEqualTo(requestExternalUrl))
        .willReturn(aResponse().withStatus(404).withBody("template not found"))
    )

    val result = controller.getGformByFormType.apply(
      FakeRequest(POST, "/get-gform")
        .withSession("authToken" -> "test-token")
        .withFormUrlEncodedBody("formTemplateId.value" -> formTemplateId.value)
    )

    assertEquals(status(result), OK)
    assertEquals(
      contentAsString(result),
      s"Problem when fetching form template: $formTemplateId. Reason: template not found"
    )
  }
}
