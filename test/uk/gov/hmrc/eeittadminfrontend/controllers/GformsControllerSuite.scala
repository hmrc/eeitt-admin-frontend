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

import com.github.tomakehurst.wiremock.client.WireMock.{ aResponse, exactly, get, getRequestedFor, stubFor, urlEqualTo, verify }
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.testkit.NoMaterializer
import munit.FunSuite
import org.mockito.Mockito.mock
import play.api.Configuration
import play.api.libs.json.{ JsObject, Json }
import play.api.mvc.ControllerComponents
import play.api.test.{ FakeRequest, Helpers }
import play.api.test.Helpers._
import uk.gov.hmrc.eeittadminfrontend.common.WiremockSupport
import uk.gov.hmrc.eeittadminfrontend.connectors.GformConnector
import uk.gov.hmrc.eeittadminfrontend.models.FormTemplateId
import uk.gov.hmrc.eeittadminfrontend.services.{ BatchUploadService, GformService }
import uk.gov.hmrc.eeittadminfrontend.validators.FormTemplateValidator
import uk.gov.hmrc.eeittadminfrontend.views.html._
import uk.gov.hmrc.http.test.HttpClientV2Support
import uk.gov.hmrc.internalauth.client.{ FrontendAuthComponents, Predicate, Retrieval }
import uk.gov.hmrc.internalauth.client.test.{ FrontendAuthComponentsStub, StubBehaviour }
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ ExecutionContext, Future }

class GformsControllerSuite extends FunSuite with WiremockSupport with HttpClientV2Support {

  // Responses are strict, so no stream needs to be materialized.
  implicit private val materializer: Materializer = NoMaterializer
  implicit private val controllerComponents: ControllerComponents = Helpers.stubControllerComponents()
  implicit private val ec: ExecutionContext = controllerComponents.executionContext
  private val formTemplateId = FormTemplateId("test-form")
  private val internalTemplate = Json.obj("formTemplateId" -> formTemplateId.value, "field" -> "value")
  private val sensitiveTemplate = Json.obj("formTemplateId" -> formTemplateId.value, "representation" -> "sensitive")
  private val requestExternalUrl = s"/gform/formtemplates/${formTemplateId.value}/sensitive"
  private val requestInternalUrl = s"/gform/formtemplates/${formTemplateId.value}/internal"
  private case class TemplateEndpoint(
    requestUrl: String,
    requestPath: String,
    template: JsObject,
    isInternal: Boolean,
    expectedError: String
  )

  private val templateEndpoints = List(
    TemplateEndpoint(
      requestInternalUrl,
      "/get-internal-gform",
      internalTemplate,
      isInternal = true,
      expectedError = s"Problem when fetching internal form template: $formTemplateId. Reason: template not found"
    ),
    TemplateEndpoint(
      requestExternalUrl,
      "/get-gform",
      sensitiveTemplate,
      isInternal = false,
      expectedError = s"Problem when fetching form template: $formTemplateId. Reason: template not found"
    )
  )

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
    mock(classOf[GformService]),
    mock(classOf[FormTemplateValidator]),
    mock(classOf[BatchUploadService]),
    Helpers.stubMessagesControllerComponents(),
    mock(classOf[gform_formtemplate_pii]),
    mock(classOf[gform_formtemplates_pii]),
    mock(classOf[gform_formtemplates_pii_home]),
    mock(classOf[gform_page]),
    mock(classOf[batch_upload]),
    mock(classOf[handlebars_template]),
    mock(classOf[confirmation])
  )

  override def beforeAll(): Unit =
    startServer()

  override def beforeEach(context: BeforeEach): Unit =
    wireMockServer.resetAll()

  override def afterAll(): Unit =
    stopServer()

  templateEndpoints.foreach { endpoint =>
    val fakeRequest = FakeRequest(POST, endpoint.requestPath)
      .withSession("authToken" -> "test-token")
      .withFormUrlEncodedBody("formTemplateId.value" -> formTemplateId.value)

    test(s"getFormTemplate on ${endpoint.requestPath} returns the JSON for the submitted form template ID") {
      stubFor(
        get(urlEqualTo(endpoint.requestUrl))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody(Json.stringify(endpoint.template))
          )
      )

      val result = controller.getFormTemplate(endpoint.isInternal).apply(fakeRequest)

      assertEquals(status(result), OK)
      assertEquals(contentAsString(result), Json.prettyPrint(endpoint.template))
      assertEquals(contentType(result), Some("text/plain"))
      verify(exactly(1), getRequestedFor(urlEqualTo(endpoint.requestUrl)))
    }

    test(
      s"getFormTemplate on ${endpoint.requestPath} responds 200 with an error message when the template lookup returns 404"
    ) {
      stubFor(
        get(urlEqualTo(endpoint.requestUrl))
          .willReturn(aResponse().withStatus(404).withBody("template not found"))
      )

      val errorResult = controller.getFormTemplate(endpoint.isInternal).apply(fakeRequest)

      assertEquals(status(errorResult), OK)
      assertEquals(contentAsString(errorResult), endpoint.expectedError)
      verify(exactly(1), getRequestedFor(urlEqualTo(endpoint.requestUrl)))
    }
  }
}
