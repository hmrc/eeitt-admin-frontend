/*
 * Copyright 2021 HM Revenue & Customs
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

import akka.http.scaladsl.model.StatusCodes
import org.slf4j.{ Logger, LoggerFactory }
import play.api.libs.json._
import uk.gov.hmrc.eeittadminfrontend.models.{ DbLookupId, FormTemplateId, GformServiceError, SubmissionPageData }
import uk.gov.hmrc.eeittadminfrontend.wshttp.WSHttp
import uk.gov.hmrc.http.{ HeaderCarrier, HttpReads, HttpReadsInstances, HttpResponse }
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.http.HttpReads.Implicits.readFromJson
import scala.concurrent.{ ExecutionContext, Future }

class GformConnector(wsHttp: WSHttp, sc: ServicesConfig) {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  implicit val legacyRawReads: HttpReads[HttpResponse] =
    HttpReadsInstances.throwOnFailure(HttpReadsInstances.readEitherOf(HttpReadsInstances.readRaw))

  val gformUrl = s"${sc.baseUrl("gform")}/gform"

  def getGformsTemplate(
    formTemplateId: FormTemplateId
  )(implicit ec: ExecutionContext): Future[Either[String, JsValue]] =
    wsHttp
      .doGet(gformUrl + s"/formtemplates/$formTemplateId/raw")
      .map { response =>
        if (response.status == 200) Right(response.json) else Left(response.body.toString)
      }
      .recover { case ex =>
        Left(s"Unknown problem when trying to retrieve template $formTemplateId: " + ex.getMessage)
      }

  def getAllSubmissons(formTemplateId: FormTemplateId, page: Int, pageSize: Int)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ) =
    wsHttp.GET[SubmissionPageData](gformUrl + s"/submissionDetails/all/${formTemplateId.value}/$page/$pageSize")

  def getAllGformsTemplates(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[JsValue] =
    wsHttp.GET[JsArray](gformUrl + "/formtemplates")

  def getAllSchema(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[JsValue] =
    wsHttp.GET[JsValue](gformUrl + "/schemas")

  def saveTemplate(
    gformTemplate: JsValue
  )(implicit ec: ExecutionContext): Future[Either[String, Unit]] =
    wsHttp
      .doPost[JsValue](gformUrl + "/formtemplates", gformTemplate, List.empty[(String, String)])
      .map { response =>
        if (response.status == 204) // Results.NoContent
          Right(())
        else
          Left(response.json.toString)
      }
      .recover { case ex =>
        val formTemplateId = gformTemplate \ "_id"
        Left(s"Unknown problem when trying to save template $formTemplateId: " + ex.getMessage)
      }

  def deleteTemplate(
    formTemplateId: FormTemplateId
  )(implicit headerCarrier: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    wsHttp.DELETE[HttpResponse](gformUrl + s"/formtemplates/$formTemplateId")

  def saveDBLookupIds(collectionName: String, dbLookupIds: Seq[DbLookupId])(implicit
    ec: ExecutionContext
  ): Future[Unit] =
    wsHttp
      .doPut(gformUrl + s"/dblookup/$collectionName", dbLookupIds, Seq.empty)
      .map { response =>
        response.status match {
          case StatusCodes.Created.intValue =>
            ()
          case _ =>
            val message =
              s"Failed to save dbLookup ids for collection $collectionName [status=${response.status}, error=${response.body}]"
            logger.error(message)
            throw GformServiceError(response.status, message)
        }
      }
}
