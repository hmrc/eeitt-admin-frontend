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

import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import uk.gov.hmrc.eeittadminfrontend.models.fileupload.EnvelopeId
import uk.gov.hmrc.eeittadminfrontend.models._
import uk.gov.hmrc.http.{ HttpReads, HttpReadsInstances, HttpResponse }
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class GformFrontendConnector @Inject() (wsHttp: DefaultHttpClient, sc: ServicesConfig) {

  implicit val legacyRawReads: HttpReads[HttpResponse] =
    HttpReadsInstances.throwOnFailure(HttpReadsInstances.readEitherOf(HttpReadsInstances.readRaw))

  val gformFrontendUrl = s"${sc.baseUrl("gform-frontend")}/submissions"

  def downloadPdf(
    formTemplateId: FormTemplateId,
    envelopeId: EnvelopeId,
    submissionTime: String,
    affinityGroup: String
  )(implicit
    ec: ExecutionContext
  ): Future[Either[String, Source[ByteString, _]]] = {
    val url =
      gformFrontendUrl + s"/recover-pdf/$formTemplateId/$envelopeId/$submissionTime/${affinityGroup.toLowerCase}"

    wsHttp
      .buildRequest(url, Seq.empty[(String, String)])
      .withMethod("GET")
      .stream()
      .map { response =>
        if (response.status == 200) Right(response.bodyAsSource) else Left(response.body)
      }
      .recover { case ex =>
        Left(s"Unknown problem when trying to download an envelopeId $envelopeId: " + ex.getMessage)
      }
  }

}
