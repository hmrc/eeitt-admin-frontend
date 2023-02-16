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

import akka.http.scaladsl.model.StatusCodes

import javax.inject.Inject
import org.slf4j.{ Logger, LoggerFactory }
import play.api.libs.json._
import uk.gov.hmrc.eeittadminfrontend.models.sdes.{ CorrelationId, NotificationStatus, SdesSubmissionData, SdesSubmissionPageData }
import uk.gov.hmrc.eeittadminfrontend.models.{ DbLookupId, DeleteResults, FormId, FormTemplateId, FormTemplateRawId, GformNotificationBanner, GformServiceError, PIIDetailsResponse, SavedForm, SavedFormDetail, SignedFormDetails, SubmissionPageData }
import uk.gov.hmrc.http.{ HeaderCarrier, HttpClient, HttpReads, HttpReadsInstances, HttpResponse }
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.http.HttpReads.Implicits.readFromJson

import scala.concurrent.{ ExecutionContext, Future }

class GformConnector @Inject() (wsHttp: HttpClient, sc: ServicesConfig) {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  implicit val legacyRawReads: HttpReads[HttpResponse] =
    HttpReadsInstances.throwOnFailure(HttpReadsInstances.readEitherOf(HttpReadsInstances.readRaw))

  val gformUrl = s"${sc.baseUrl("gform")}/gform"

  def getGformsTemplate(
    formTemplateId: FormTemplateId
  )(implicit ec: ExecutionContext): Future[Either[String, JsValue]] = {
    val url = gformUrl + s"/formtemplates/$formTemplateId/sensitive"
    wsHttp
      .doGet(url)
      .map { response =>
        logger.info(s"Get $formTemplateId from gform ${response.status}")
        if (response.status == 200) Right(response.json)
        else {
          logger.error(s"Wrong status code ${response.body} when calling $url, response body ${response.body}")
          Left(response.body)
        }
      }
      .recover { case ex =>
        val message =
          s"Unknown problem when trying to retrieve template $formTemplateId, by calling $url, exception: " + ex.getMessage
        logger.error(message, ex)
        Left(message)
      }
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
    formTemplateId: FormTemplateId,
    gformTemplate: JsValue
  )(implicit ec: ExecutionContext): Future[Either[String, Unit]] =
    wsHttp
      .doPost[JsValue](gformUrl + "/formtemplates", gformTemplate, List.empty[(String, String)])
      .map { response =>
        if (response.status == 204) // Results.NoContent
          Right(())
        else
          Left((Json.parse(response.body) \ "error").as[String])
      }
      .recover { case ex =>
        val message = s"Unknown problem when trying to save template $formTemplateId: " + ex.getMessage
        logger.error(message, ex)
        Left(message)
      }

  def deleteTemplate(
    formTemplateId: FormTemplateId
  )(implicit headerCarrier: HeaderCarrier, ec: ExecutionContext): Future[DeleteResults] =
    wsHttp
      .DELETE[DeleteResults](gformUrl + s"/formtemplates/$formTemplateId/sensitive")

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

  def getTitlesWithPII(
    formTemplateRawId: FormTemplateRawId,
    filters: List[String],
    includeJson: Boolean
  )(implicit headerCarrier: HeaderCarrier, ec: ExecutionContext): Future[PIIDetailsResponse] =
    wsHttp.GET[PIIDetailsResponse](
      gformUrl + s"/formtemplates/get-titles-with-pii/${formTemplateRawId.value}?includeJson=$includeJson${if (filters.isEmpty) ""
      else "&filters=" + filters.mkString(",")}"
    )

  def getFormCount(
    formTemplateId: FormTemplateId
  )(implicit headerCarrier: HeaderCarrier, ec: ExecutionContext): Future[SavedForm] =
    wsHttp.GET[SavedForm](gformUrl + s"/formStatistics/${formTemplateId.value}")

  def getFormDetailCount(
    formTemplateId: FormTemplateId
  )(implicit headerCarrier: HeaderCarrier, ec: ExecutionContext): Future[Seq[SavedFormDetail]] =
    wsHttp.GET[Seq[SavedFormDetail]](gformUrl + s"/formStatistics/${formTemplateId.value}/details")

  def getSignedFormsDetails(implicit
    headerCarrier: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Seq[SignedFormDetails]] =
    wsHttp.GET[Seq[SignedFormDetails]](gformUrl + s"/formStatistics/signed-forms")

  def deleteForm(
    formId: FormId
  )(implicit ec: ExecutionContext): Future[HttpResponse] =
    wsHttp
      .doPost[String](gformUrl + s"/forms/${formId.value}/delete", "", List.empty[(String, String)])

  def unstuckForm(
    formId: FormId
  )(implicit ec: ExecutionContext): Future[HttpResponse] =
    wsHttp
      .doPost[String](gformUrl + s"/forms/${formId.value}/unstuck", "", List.empty[(String, String)])

  def getSdesSubmissions(
    page: Int,
    pageSize: Int,
    maybeProcessed: Option[Boolean] = None,
    formTemplateId: Option[FormTemplateId],
    status: Option[NotificationStatus],
    showBeforeDate: Option[Boolean]
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ) = {
    val queryStringByProcessed = maybeProcessed.fold("")(p => s"processed=$p")
    val queryStringByTemplateId = formTemplateId.fold(queryStringByProcessed)(formTemplateId =>
      s"formTemplateId=$formTemplateId&$queryStringByProcessed"
    )
    val queryStringByStatus = status.fold(queryStringByTemplateId)(status => s"status=$status&$queryStringByTemplateId")
    val queryString =
      showBeforeDate.fold(queryStringByStatus)(showBeforeDate => s"showBeforeDate=$showBeforeDate&$queryStringByStatus")
    wsHttp.GET[SdesSubmissionPageData](gformUrl + s"/sdes/search/$page/$pageSize?$queryString")
  }

  def getSdesSubmission(correlationId: CorrelationId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ) =
    wsHttp.GET[SdesSubmissionData](gformUrl + s"/sdes/${correlationId.value}")

  def notifySDES(correlationId: CorrelationId)(implicit ec: ExecutionContext): Future[HttpResponse] =
    wsHttp
      .doPost[String](gformUrl + s"/sdes/notify/${correlationId.value}", "")

  def deleteSdesSubmission(correlationId: CorrelationId)(implicit ec: ExecutionContext): Future[HttpResponse] =
    wsHttp.doDelete(gformUrl + s"/sdes/${correlationId.value}")

  def findNotificationBanner()(implicit
    ec: ExecutionContext
  ): Future[Option[GformNotificationBanner]] =
    wsHttp.doGet(gformUrl + s"/notification-banner").map { response =>
      if (response.status == 200) Some(response.json.as[GformNotificationBanner])
      else Option.empty[GformNotificationBanner]
    }

  def deleteNotificationBanner()(implicit
    ec: ExecutionContext
  ): Future[Unit] =
    wsHttp.doDelete(gformUrl + s"/notification-banner").map(_ => ())

  def saveNotificationBanner(notificationBanner: GformNotificationBanner)(implicit
    ec: ExecutionContext
  ): Future[Unit] =
    wsHttp.doPost[GformNotificationBanner](gformUrl + s"/notification-banner", notificationBanner).map(_ => ())

}
