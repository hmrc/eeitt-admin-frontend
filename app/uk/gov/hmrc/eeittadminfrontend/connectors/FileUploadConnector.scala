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

import akka.stream.scaladsl.Source
import akka.util.ByteString
import play.api.Logger
import play.api.libs.json._
import uk.gov.hmrc.eeittadminfrontend.models.fileupload.EnvelopeId
import uk.gov.hmrc.eeittadminfrontend.wshttp.WSHttp
import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse }
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ ExecutionContext, Future }

class FileUploadConnector(wsHttp: WSHttp, sc: ServicesConfig) {
  val fileUploadUrl = sc.baseUrl("file-upload")

  def getEnvelopeById(
    envelopeId: EnvelopeId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[String, JsValue]] = {
    val url = fileUploadUrl + s"/file-upload/envelopes/${envelopeId.value}"
    wsHttp
      .doGet(url)
      .map { response =>
        if (response.status == 200) Right(response.json) else Left(response.body.toString)
      }
      .recover {
        case ex =>
          Left(s"Unknown problem when trying to retrieve envelopeId $envelopeId: " + ex.getMessage)
      }
  }

  def downloadEnvelopeId(envelopeId: EnvelopeId)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Either[String, Source[ByteString, _]]] = {
    val url = fileUploadUrl + s"/file-transfer/envelopes/${envelopeId.value}"
    wsHttp
      .buildRequest(url)
      .get
      .map { response =>
        if (response.status == 200) Right(response.bodyAsSource) else Left(response.body)
      }
      .recover {
        case ex =>
          Left(s"Unknown problem when trying to download an envelopeId $envelopeId: " + ex.getMessage)
      }
  }

  def archiveEnvelopeId(envelopeId: EnvelopeId)(
    implicit headerCarrier: HeaderCarrier,
    ec: ExecutionContext): Future[Either[String, String]] = {

    val url = fileUploadUrl + s"/file-transfer/envelopes/${envelopeId.value}"

    wsHttp
      .DELETE[HttpResponse](url)
      .map { response =>
        val success =
          s"Envelope $envelopeId archived successfully. Status: ${response.status}, body: ${response.body}"
        Logger.info(success)
        Right(success)
      }
      .recover {
        case ex =>
          Left(s"Unknown problem when trying to archive envelopeId $envelopeId: " + ex.getMessage)
      }
  }
}
