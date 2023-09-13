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
import uk.gov.hmrc.eeittadminfrontend.history.{ HistoryFilter, HistoryId, HistoryOverview, HistoryOverviewFull }
import uk.gov.hmrc.eeittadminfrontend.models.CircePlayHelpers
import uk.gov.hmrc.eeittadminfrontend.models.fileupload.EnvelopeId
import uk.gov.hmrc.eeittadminfrontend.models.sdes.{ CorrelationId, NotificationStatus, ProcessingStatus, SdesDestination, SdesSubmissionData, SdesSubmissionPageData, SdesWorkItemData, SdesWorkItemPageData }
import uk.gov.hmrc.eeittadminfrontend.models.{ BannerId, DbLookupId, DeleteResult, DeleteResults, FormId, FormRedirectPageData, FormTemplateId, FormTemplateRaw, FormTemplateRawId, GformNotificationBanner, GformNotificationBannerFormTemplate, GformNotificationBannerView, GformServiceError, PIIDetailsResponse, SavedForm, SavedFormDetail, Shutter, ShutterFormTemplate, ShutterMessageId, ShutterView, SignedFormDetails, SubmissionPageData }
import uk.gov.hmrc.http.{ HeaderCarrier, HttpClient, HttpReads, HttpReadsInstances, HttpResponse }
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.http.HttpReads.Implicits.{ readFromJson, readOptionOfNotFound }

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
    showBeforeAt: Option[Boolean],
    destination: Option[SdesDestination]
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ) = {
    val queryStringByProcessed = maybeProcessed.fold("")(p => s"processed=$p")
    val queryStringByTemplateId = formTemplateId.fold(queryStringByProcessed)(formTemplateId =>
      s"formTemplateId=$formTemplateId&$queryStringByProcessed"
    )
    val queryStringByStatus = status.fold(queryStringByTemplateId)(status => s"status=$status&$queryStringByTemplateId")
    val queryStringByDestination =
      destination.fold(queryStringByStatus)(destination => s"destination=$destination&$queryStringByStatus")
    val queryString =
      showBeforeAt.fold(queryStringByDestination)(showBeforeAt =>
        s"showBeforeAt=$showBeforeAt&$queryStringByDestination"
      )
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

  def searchDmsWorkItem(
    page: Int,
    pageSize: Int,
    formTemplateId: Option[FormTemplateId],
    status: Option[ProcessingStatus]
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ) = {
    val queryStringByFormTemplate = formTemplateId.fold("")(id => s"formTemplateId=$id")
    val queryString = status.fold(queryStringByFormTemplate)(s => s"status=${s.name}&$queryStringByFormTemplate")
    wsHttp.GET[SdesWorkItemPageData](gformUrl + s"/dms-work-item/search/$page/$pageSize?$queryString")
  }

  def getDmsWorkItem(id: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[SdesWorkItemData] =
    wsHttp.GET[SdesWorkItemData](gformUrl + s"/dms-work-item/$id")

  def enqueueDmsWorkItem(id: String)(implicit ec: ExecutionContext): Future[HttpResponse] =
    wsHttp.doPost[String](gformUrl + s"/dms-work-item/enqueue/$id", "")

  def deleteDmsWorkItem(id: String)(implicit ec: ExecutionContext): Future[HttpResponse] =
    wsHttp.doDelete(gformUrl + s"/dms-work-item/$id")

  def searchDataStoreWorkItem(
    page: Int,
    pageSize: Int,
    formTemplateId: Option[FormTemplateId],
    status: Option[ProcessingStatus]
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ) = {
    val queryStringByFormTemplate = formTemplateId.fold("")(id => s"formTemplateId=$id")
    val queryString = status.fold(queryStringByFormTemplate)(s => s"status=${s.name}&$queryStringByFormTemplate")
    wsHttp.GET[SdesWorkItemPageData](gformUrl + s"/data-store-work-item/search/$page/$pageSize?$queryString")
  }

  def getDataStoreWorkItem(id: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[SdesWorkItemData] =
    wsHttp.GET[SdesWorkItemData](gformUrl + s"/data-store-work-item/$id")

  def enqueueDataStoreWorkItem(id: String)(implicit ec: ExecutionContext): Future[HttpResponse] =
    wsHttp.doPost[String](gformUrl + s"/data-store-work-item/enqueue/$id", "")

  def deleteDataStoreWorkItem(id: String)(implicit ec: ExecutionContext): Future[HttpResponse] =
    wsHttp.doDelete(gformUrl + s"/data-store-work-item/$id")

  def findNotificationBanner()(implicit
    ec: ExecutionContext
  ): Future[List[GformNotificationBannerView]] =
    wsHttp.doGet(gformUrl + s"/notification-banner").map { response =>
      if (response.status == 200) response.json.as[List[GformNotificationBannerView]]
      else List.empty[GformNotificationBannerView]
    }

  def deleteNotificationBanner(bannerId: BannerId)(implicit
    ec: ExecutionContext
  ): Future[Unit] =
    wsHttp.doDelete(gformUrl + s"/notification-banner/${bannerId.value}").map(_ => ())

  def saveNotificationBanner(notificationBanner: GformNotificationBanner)(implicit
    ec: ExecutionContext
  ): Future[Unit] =
    wsHttp.doPost[GformNotificationBanner](gformUrl + s"/notification-banner", notificationBanner).map(_ => ())

  def saveNotificationBannerFormTemplates(notificationBannerFormTemplate: GformNotificationBannerFormTemplate)(implicit
    ec: ExecutionContext
  ): Future[Unit] =
    wsHttp
      .doPost[GformNotificationBannerFormTemplate](
        gformUrl + s"/notification-banner-form-template",
        notificationBannerFormTemplate
      )
      .map(_ => ())

  def deleteNotificationBannerFormTemplate(formTemplateId: FormTemplateId)(implicit
    ec: ExecutionContext
  ): Future[Unit] =
    wsHttp.doDelete(gformUrl + s"/notification-banner-form-template/${formTemplateId.value}").map(_ => ())

  def findShutter()(implicit
    ec: ExecutionContext
  ): Future[List[ShutterView]] =
    wsHttp.doGet(gformUrl + s"/shutter").map { response =>
      if (response.status == 200) response.json.as[List[ShutterView]]
      else List.empty[ShutterView]
    }

  def deleteShutter(shutterMessageId: ShutterMessageId)(implicit
    ec: ExecutionContext
  ): Future[Unit] =
    wsHttp.doDelete(gformUrl + s"/shutter/${shutterMessageId.value}").map(_ => ())

  def saveShutter(shutter: Shutter)(implicit
    ec: ExecutionContext
  ): Future[Unit] =
    wsHttp.doPost[Shutter](gformUrl + s"/shutter", shutter).map(_ => ())

  def saveShutterFormTemplates(shutterFormTemplate: ShutterFormTemplate)(implicit
    ec: ExecutionContext
  ): Future[Unit] =
    wsHttp
      .doPost[ShutterFormTemplate](
        gformUrl + s"/shutter-form-template",
        shutterFormTemplate
      )
      .map(_ => ())

  def deleteShutterFormTemplate(formTemplateId: FormTemplateId)(implicit
    ec: ExecutionContext
  ): Future[Unit] =
    wsHttp.doDelete(gformUrl + s"/shutter-form-template/${formTemplateId.value}").map(_ => ())

  def getFormTemplatesRedirects(page: Int, pageSize: Int)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ) =
    wsHttp.GET[FormRedirectPageData](gformUrl + s"/formtemplates-redirects/$page/$pageSize")

  def getHandlebarsTemplate(
    formTemplateId: FormTemplateId
  )(implicit ec: ExecutionContext): Future[Either[String, String]] = {
    val url = gformUrl + s"/handlebarstemplates/${formTemplateId.value}"
    wsHttp
      .doGet(url, headers = Seq("Content-Type" -> "text/plain;charset=UTF-8"))
      .map { response =>
        logger.info(s"Get ${formTemplateId.value} handlebars from gform ${response.status}")
        if (response.status == 200) Right(response.body)
        else {
          logger.error(s"Wrong status code ${response.body} when calling $url, response body ${response.body}")
          Left(response.body)
        }
      }
      .recover { case ex =>
        val message =
          s"Unknown problem when trying to retrieve template ${formTemplateId.value}, by calling $url, exception: " + ex.getMessage
        logger.error(message, ex)
        Left(message)
      }
  }

  def getRawHandlebarsTemplate(
    formTemplateId: FormTemplateId
  )(implicit ec: ExecutionContext): Future[Either[String, String]] = {
    val url = gformUrl + s"/handlebarstemplates/${formTemplateId.value}/raw"
    wsHttp
      .doGet(url, headers = Seq("Content-Type" -> "text/plain;charset=UTF-8"))
      .map { response =>
        logger.info(s"Get ${formTemplateId.value} handlebars from gform ${response.status}")
        if (response.status == 200) Right(response.body)
        else {
          logger.error(s"Wrong status code ${response.body} when calling $url, response body ${response.body}")
          Left(response.body)
        }
      }
      .recover { case ex =>
        val message =
          s"Unknown problem when trying to retrieve template ${formTemplateId.value}, by calling $url, exception: " + ex.getMessage
        logger.error(message, ex)
        Left(message)
      }
  }

  def deleteHandlebarsTemplate(
    formTemplateId: FormTemplateId
  )(implicit headerCarrier: HeaderCarrier, ec: ExecutionContext): Future[DeleteResult] =
    wsHttp.DELETE[DeleteResult](gformUrl + s"/handlebarstemplates/${formTemplateId.value}")

  def saveHandlebarsTemplate(formTemplateId: FormTemplateId, payload: String)(implicit
    ec: ExecutionContext
  ): Future[Either[String, Unit]] =
    wsHttp
      .doPostString(
        gformUrl + s"/handlebarstemplates/${formTemplateId.value}",
        payload,
        headers = Seq("Content-Type" -> "text/plain;charset=UTF-8")
      )
      .map { response =>
        if (response.status == 204)
          Right(())
        else
          Left(s"error: ${response.body}")
      }
      .recover { case ex =>
        val message =
          s"Unknown problem when trying to save handlebars template ${formTemplateId.value}: " + ex.getMessage
        logger.error(message, ex)
        Left(message)
      }

  def getAllHandlebarsTemplates(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[JsValue] =
    wsHttp.GET[JsArray](gformUrl + "/handlebarstemplates")

  def getHandlebarsTemplateIds(
    formTemplateId: FormTemplateId
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[JsValue] =
    wsHttp.GET[JsArray](gformUrl + s"/formtemplates-with-handlebars/$formTemplateId")

  def getEnvelopeById(
    envelopeId: EnvelopeId
  )(implicit ec: ExecutionContext): Future[Either[String, JsValue]] = {
    val url = gformUrl + s"/envelopes/${envelopeId.value}"
    wsHttp
      .doGet(url)
      .map { response =>
        if (response.status == 200) Right(response.json) else Left(s"Envelope ${envelopeId.value} not found")
      }
      .recover { case ex =>
        Left(s"Unknown problem when trying to retrieve envelopeId $envelopeId: " + ex.getMessage)
      }
  }

  def historyAllTemplateIds(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[List[FormTemplateRawId]] =
    wsHttp.GET[List[String]](gformUrl + "/history-all-ids").map(_.map(FormTemplateRawId(_)))

  def historyOverviewForTemplateId(
    formTemplateRawId: FormTemplateRawId
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[List[HistoryOverview]] =
    wsHttp.GET[List[HistoryOverview]](gformUrl + "/history/overview/" + formTemplateRawId.value)

  def historyTemplate(historyId: HistoryId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[FormTemplateRaw] =
    wsHttp.GET[JsValue](gformUrl + "/history/template/" + historyId.value).map { json =>
      FormTemplateRaw(CircePlayHelpers.playToCirceUnsafe(json))
    }

  def nextHistoryOverview(
    formTemplateRawId: FormTemplateRawId,
    historyId: HistoryId
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[HistoryId]] =
    wsHttp
      .GET[Option[HistoryId]](
        gformUrl + s"/history/next/" + formTemplateRawId.value + "/" + historyId.value
      )

  def historyWithFilter(
    historyFilter: HistoryFilter
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[List[HistoryOverviewFull]] =
    wsHttp
      .POST[HistoryFilter, List[HistoryOverviewFull]](
        gformUrl + s"/history/filter",
        historyFilter
      )
}
