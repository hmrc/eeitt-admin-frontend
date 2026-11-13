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

package uk.gov.hmrc.eeittadminfrontend.connectors

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{ Millis, Seconds, Span }
import play.api.Configuration
import play.api.libs.json.Json
import uk.gov.hmrc.eeittadminfrontend.common.WiremockSupport
import uk.gov.hmrc.eeittadminfrontend.models.FormTemplateId
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.HttpClientV2Support
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.ExecutionContext.Implicits.global

class GformConnectorSpec
    extends AnyFlatSpecLike with Matchers with ScalaFutures with WiremockSupport with BeforeAndAfterAll
    with HttpClientV2Support {

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(Span(5, Seconds), Span(500, Millis))
  implicit private val headerCarrier: HeaderCarrier = HeaderCarrier()

  override protected def beforeAll(): Unit =
    startServer()

  override protected def afterAll(): Unit =
    stopServer()

  private val formTemplateId = FormTemplateId("test-form")
  private val internalTemplate = Json.obj("formTemplateId" -> formTemplateId.value, "field" -> "value")
  private val requestUrl = s"/gform/formtemplates/${formTemplateId.value}/internal"

  private val connector = new GformConnector(
    mkHttpClientV2(),
    new ServicesConfig(
      Configuration(
        "microservice.services.gform.host" -> "localhost",
        "microservice.services.gform.port" -> wiremockPort.toString
      )
    )
  )

  "getGformsTemplate" should "return the internal template from the internal endpoint" in {
    stubFor(
      get(urlEqualTo(requestUrl))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(Json.stringify(internalTemplate))
        )
    )

    whenReady(connector.getGformsTemplate(formTemplateId, true)) { result =>
      result shouldBe Right(internalTemplate)
    }

    verify(getRequestedFor(urlEqualTo(requestUrl)))
  }

  it should "return the downstream response body when the internal endpoint fails" in {
    stubFor(
      get(urlEqualTo(requestUrl))
        .willReturn(aResponse().withStatus(404).withBody("template not found"))
    )

    whenReady(connector.getGformsTemplate(formTemplateId, true)) { result =>
      result shouldBe Left("template not found")
    }
  }
}
