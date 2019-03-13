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

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import org.scalatestplus.play.{ BaseOneAppPerSuite, FakeApplicationFactory }
import play.api.libs.json.{ JsValue, Json }
import play.api.libs.ws.ahc.{ AhcConfigBuilder, AhcWSClient }
import uk.gov.hmrc.eeittadminfrontend.ApplicationComponentsOnePerSuite
import uk.gov.hmrc.eeittadminfrontend.models.{ Identifier, Verifier }
import uk.gov.hmrc.eeittadminfrontend.stubs.UserDetailsStubs
import uk.gov.hmrc.play.test.UnitSpec

class DeltaControllerSpec extends UnitSpec with ApplicationComponentsOnePerSuite { //with UserDetailsStubs {

  val builder: AhcConfigBuilder = new AhcConfigBuilder()
  val ahcBuilder: DefaultAsyncHttpClientConfig.Builder = builder.configure()
  val ahcConfig: DefaultAsyncHttpClientConfig = ahcBuilder.build()
  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val client: AhcWSClient = new AhcWSClient(ahcConfig)

  val updateEnrolmentUrl = s"http://localhost:9000/eeitt-admin-frontend/delta/enrolment/add"

  def doPost(body: JsValue) = client.url(updateEnrolmentUrl).post(body)

  val applicationStart = fakeApplication

  println("asdasdas" + applicationStart)

  val validDetailsForEnrolment = MigrationData(
    "validGroupId",
    identifiers = List(Identifier("someKey", "someValue")),
    verifiers = List(Verifier("someKey", "someValue")))

  "create enrolment for groupId" should {

    "200 successfully created enrolment for groupId with identifiers & verifiers provided" in {
      //      given202SendEmailReject(validDetailsForEnrolment.groupId)
      await(doPost(Json.toJson(validDetailsForEnrolment))).status shouldBe 200
    }

  }
}
