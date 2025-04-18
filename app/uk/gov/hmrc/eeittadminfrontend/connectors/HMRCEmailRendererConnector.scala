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

import org.apache.pekko.http.scaladsl.model.{ StatusCode, StatusCodes }
import javax.inject.Inject
import org.slf4j.LoggerFactory
import play.api.libs.json.Json
import uk.gov.hmrc.eeittadminfrontend.models.email.{ EmailRenderRequest, EmailRenderResponse, NotFound, ParametersNotFound, Successful, Unexpected }
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http._

import scala.concurrent.{ ExecutionContext, Future }
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

class HMRCEmailRendererConnector @Inject() (wsHttp: HttpClientV2, sc: ServicesConfig)(implicit ec: ExecutionContext)
    extends HttpReadsHttpResponse {

  val baseUrl = sc.baseUrl("hmrc-email-renderer")

  private val logger = LoggerFactory.getLogger(getClass)

  implicit val httpReads: HttpReads[EmailRenderResponse] = readRaw.map { httpResponse =>
    StatusCode.int2StatusCode(httpResponse.status) match {
      case StatusCodes.OK         => Successful
      case StatusCodes.NotFound   => NotFound
      case StatusCodes.BadRequest => httpResponse.json.as[ParametersNotFound]
      case other =>
        Unexpected(
          s"Unexpected response from hmrc-email-renderer render API [status=${other.intValue()}, body=${httpResponse.body}]"
        )
    }
  }

  def renderTemplate(
    emailRenderRequest: EmailRenderRequest
  )(implicit headerCarrier: HeaderCarrier): Future[EmailRenderResponse] = {
    logger.info(s"Render email template ${emailRenderRequest.templateId}")
    wsHttp
      .post(
        url"$baseUrl/templates/${emailRenderRequest.templateId}"
      )
      .withBody(Json.toJson(emailRenderRequest))
      .execute[EmailRenderResponse]
  }
}
