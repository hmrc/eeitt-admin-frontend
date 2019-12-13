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

package uk.gov.hmrc.eeittadminfrontend.connectors

import play.api.Logger
import play.api.libs.json._
import uk.gov.hmrc.eeittadminfrontend.models.EnvelopeId
import uk.gov.hmrc.eeittadminfrontend.{ InjectionDodge, WSHttp }
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse }

import scala.concurrent.{ ExecutionContext, Future }

object FileUploadConnector {

  private val sc = new ServicesConfig {
    override protected def mode = InjectionDodge.mode
    override protected val runModeConfiguration = InjectionDodge.runModeConfiguration
  }
  val fileUploadUrl = sc.baseUrl("file-upload")

  def getEnvelopeById(
    envelopeId: EnvelopeId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[String, JsValue]] = {
    val url = fileUploadUrl + s"/file-upload/envelopes/${envelopeId.value}"
    WSHttp
      .doGet(url)
      .map { response =>
        if (response.status == 200) Right(response.json) else Left(response.body.toString)
      }
      .recover {
        case ex =>
          Left(s"Unknown problem when trying to retrieve envelopeId $envelopeId: " + ex.getMessage)
      }
  }

  def deleteEnvelopeId(envelopeId: EnvelopeId)(
    implicit headerCarrier: HeaderCarrier,
    ec: ExecutionContext): Future[Either[String, String]] = {

    val url = fileUploadUrl + s"/file-upload/envelopes/${envelopeId.value}"

    WSHttp
      .DELETE[HttpResponse](url)
      .map { response =>
        val success =
          s"Envelope $envelopeId deleted successfully. Status: ${response.status}, body: ${response.body.toString}"
        Logger.info(success)
        Right(success)
      }
      .recover {
        case ex =>
          Left(s"Unknown problem when trying to delete envelopeId $envelopeId: " + ex.getMessage)
      }
  }
}
