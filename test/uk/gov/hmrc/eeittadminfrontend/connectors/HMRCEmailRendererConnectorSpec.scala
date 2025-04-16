/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.eeittadminfrontend.connectors

import com.github.tomakehurst.wiremock.client.WireMock.{ aResponse, post, stubFor, urlEqualTo }
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.time.{ Millis, Seconds, Span }
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import play.api.Configuration
import uk.gov.hmrc.eeittadminfrontend.common.WiremockSupport
import uk.gov.hmrc.eeittadminfrontend.models.email.{ EmailRenderRequest, NotFound, ParametersNotFound, Successful, Unexpected }
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.HttpClientV2Support

import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

class HMRCEmailRendererConnectorSpec
    extends AnyFlatSpecLike with Matchers with ScalaFutures with WiremockSupport with BeforeAndAfterAll
    with HttpClientV2Support {

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(Span(5, Seconds), Span(500, Millis))

  override protected def beforeAll(): Unit =
    startServer()

  override protected def afterAll(): Unit =
    stopServer()

  trait TestFixture {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    private val servicesConfig =
      new ServicesConfig(
        Configuration(
          "microservice.services.hmrc-email-renderer.host" -> "localhost",
          "microservice.services.hmrc-email-renderer.port" -> wiremockPort.toString
        )
      )
    val hmrcEmailRendererConnector = new HMRCEmailRendererConnector(mkHttpClientV2(), servicesConfig)
  }

  "renderTemplate" should "return a Successful response, when downstream service returns 200" in new TestFixture {
    val emailRenderRequest = EmailRenderRequest("some-template-id", Map("param1" -> "value1"))
    stubFor(
      post(urlEqualTo(s"/templates/some-template-id"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody("")
        )
    )
    whenReady(hmrcEmailRendererConnector.renderTemplate(emailRenderRequest)) { result =>
      result shouldBe Successful
    }
  }

  it should "return a NotFound response, when downstream service returns 404" in new TestFixture {
    val emailRenderRequest = EmailRenderRequest("some-template-id", Map("param1" -> "value1"))
    stubFor(
      post(urlEqualTo(s"/templates/some-template-id"))
        .willReturn(
          aResponse()
            .withStatus(404)
        )
    )
    whenReady(hmrcEmailRendererConnector.renderTemplate(emailRenderRequest)) { result =>
      result shouldBe NotFound
    }
  }

  it should "return a ParametersNotFound response, when downstream service returns 400" in new TestFixture {
    val emailRenderRequest = EmailRenderRequest("some-template-id", Map.empty)
    stubFor(
      post(urlEqualTo(s"/templates/some-template-id"))
        .willReturn(
          aResponse()
            .withStatus(400)
            .withBody("""
                        |{
                        |   "status": "Rendering of template failed",
                        |   "reason": "key not found: param1"
                        |}
                        |""".stripMargin)
        )
    )
    whenReady(hmrcEmailRendererConnector.renderTemplate(emailRenderRequest)) { result =>
      result shouldBe ParametersNotFound("key not found: param1")
    }
  }

  it should "return a Unexpected response, when downstream service returns 500" in new TestFixture {
    val emailRenderRequest = EmailRenderRequest("some-template-id", Map.empty)
    stubFor(
      post(urlEqualTo(s"/templates/some-template-id"))
        .willReturn(
          aResponse()
            .withStatus(500)
            .withBody("Internal server error")
        )
    )
    whenReady(hmrcEmailRendererConnector.renderTemplate(emailRenderRequest)) { result =>
      result shouldBe Unexpected(
        "Unexpected response from hmrc-email-renderer render API [status=500, body=Internal server error]"
      )
    }
  }
}
