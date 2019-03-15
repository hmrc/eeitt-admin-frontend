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

import play.api.libs.json.Json
import play.api.test.FakeRequest
import uk.gov.hmrc.eeittadminfrontend.ApplicationComponentsOnePerSuite
import uk.gov.hmrc.eeittadminfrontend.models.MigrationDataQuery
import uk.gov.hmrc.eeittadminfrontend.stubs.EnrolmentStoreProxyStubs
import uk.gov.hmrc.play.test.UnitSpec

class QueryControllerSpec extends UnitSpec with ApplicationComponentsOnePerSuite with EnrolmentStoreProxyStubs {

  override def additionalConfiguration: Map[String, Any] =
    Map("microservice.services.enrolment-store-proxy.port" -> wireMockPort)

  val queryController: QueryController = new QueryController(new FakeAuthConnector, messageApi)

  val validGroupId: String = "validGroupId"
  val validMigrationDataQuery: MigrationDataQuery = MigrationDataQuery(validGroupId)

  "queryEnrolments" should {
    "fetch enrolments" in {
      val request = FakeRequest()
        .withJsonBody(Json.toJson(validMigrationDataQuery))
        .withSession("token" -> "someGoogleAuthenticationToken")

      get200Es3queryEnrolments(validGroupId)
      val result = await(queryController.queryEnrolments()(request))

      jsonBodyOf(result) shouldBe Json.parse("""{
                                               |    "service":"IR-SA",
                                               |    "enrolmentDate":"2018-10-05 14:42:00",
                                               |    "status":"Activated",
                                               |    "friendlyName":"Onizukas Self Assessment",
                                               |    "identifiers": [
                                               |          {
                                               |            "key": "UTR",
                                               |            "value": "1713501890"
                                               |          }
                                               |    ]
                                               |}""".stripMargin)
    }

    "204 groupId found but no enrolments found" in {
      val request = FakeRequest()
        .withJsonBody(Json.toJson(validMigrationDataQuery))
        .withSession("token" -> "someGoogleAuthenticationToken")

      getEs3queryEnrolments(validGroupId, 204)

      val result = await(queryController.queryEnrolments()(request))
      status(result) shouldBe 204
    }

    "303 unauthorised" in {
      val request = FakeRequest()
        .withJsonBody(Json.toJson(validMigrationDataQuery))

      val result = await(queryController.queryEnrolments()(request))
      status(result) shouldBe 303
      result.header.headers.get("Location") shouldBe Some("/eeitt-admin-frontend/login")
    }

    "500 when enrolment-store-proxy returns 404 as groupId not found" in {
      val request = FakeRequest()
        .withJsonBody(Json.toJson(validMigrationDataQuery))
        .withSession("token" -> "someGoogleAuthenticationToken")

      getEs3queryEnrolments(validGroupId, 404)

      val result = await(queryController.queryEnrolments()(request))
      status(result) shouldBe 500
    }

    "500 when enrolment-store-proxy returns 400" in {
      val request = FakeRequest()
        .withJsonBody(Json.toJson(validMigrationDataQuery))
        .withSession("token" -> "someGoogleAuthenticationToken")

      getEs3queryEnrolments(validGroupId, 400)

      val result = await(queryController.queryEnrolments()(request))
      status(result) shouldBe 500
    }
  }
}
