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

package uk.gov.hmrc.eeittadminfrontend.connectors

import play.api.libs.json._
import uk.gov.hmrc.eeittadminfrontend.models.FormTemplateId
import uk.gov.hmrc.eeittadminfrontend.wshttp.WSHttp
import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse }
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ ExecutionContext, Future }

class GformConnector(wsHttp: WSHttp, sc: ServicesConfig) {

  val gformUrl = s"${sc.baseUrl("gform")}/gform"

  def getGformsTemplate(
    formTemplateId: FormTemplateId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[String, JsValue]] =
    wsHttp
      .doGet(gformUrl + s"/formtemplates/$formTemplateId/raw")
      .map { response =>
        if (response.status == 200) Right(response.json) else Left(response.body.toString)
      }
      .recover {
        case ex =>
          Left(s"Unknown problem when trying to retrieve template $formTemplateId: " + ex.getMessage)
      }

  def getAllSubmissons(formTemplateId: FormTemplateId)(implicit hc: HeaderCarrier, ec: ExecutionContext) =
    wsHttp.GET[JsArray](gformUrl + s"/submissionDetails/all/${formTemplateId.value}")

  def getAllGformsTemplates(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[JsValue] =
    wsHttp.GET[JsArray](gformUrl + "/formtemplates")

  def getAllSchema(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[JsValue] =
    wsHttp.GET[JsValue](gformUrl + "/schemas")

  def saveTemplate(
    gformTemplate: JsValue)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[String, Unit]] =
    wsHttp
      .doPost[JsValue](gformUrl + "/formtemplates", gformTemplate, List.empty[(String, String)])
      .map { response =>
        if (response.status == 204) { // Results.NoContent
          Right(())
        } else {
          Left(response.json.toString)
        }
      }
      .recover {
        case ex =>
          val formTemplateId = (gformTemplate \ "_id")
          Left(s"Unknown problem when trying to save template $formTemplateId: " + ex.getMessage)
      }

  def deleteTemplate(
    formTemplateId: FormTemplateId)(implicit headerCarrier: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    wsHttp.DELETE[HttpResponse](gformUrl + s"/formtemplates/$formTemplateId")
}
