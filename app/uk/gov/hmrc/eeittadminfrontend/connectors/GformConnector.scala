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

import cats.implicits.catsSyntaxEq
import org.apache.pekko.http.scaladsl.model.StatusCodes

import javax.inject.Inject
import org.slf4j.{ Logger, LoggerFactory }
import play.api.libs.json._
import uk.gov.hmrc.eeittadminfrontend.history.{ HistoryFilter, HistoryId, HistoryOverview, HistoryOverviewFull }
import uk.gov.hmrc.eeittadminfrontend.models.{ AllSavedVersions, BannerId, CircePlayHelpers, DbLookupId, DeleteResult, DeleteResults, FormId, FormRedirectPageData, FormTemplateId, FormTemplateRaw, FormTemplateRawId, GformNotificationBanner, GformNotificationBannerFormTemplate, GformNotificationBannerView, GformServiceError, HandlebarsSchema, PIIDetailsResponse, SavedFormDetail, SdesSubmissionsStats, Shutter, ShutterFormTemplate, ShutterMessageId, ShutterView, SignedFormDetails, SubmissionPageData, VersionStats }
import uk.gov.hmrc.eeittadminfrontend.models.fileupload.EnvelopeId
import uk.gov.hmrc.eeittadminfrontend.models.logging.CustomerDataAccessLog
import uk.gov.hmrc.eeittadminfrontend.models.sdes.SdesDestination.Dms
import uk.gov.hmrc.eeittadminfrontend.models.sdes.{ CorrelationId, ProcessingStatus, SdesDestination, SdesFilter, SdesHistoryView, SdesSubmission, SdesSubmissionData, SdesSubmissionPageData, SdesWorkItemData, SdesWorkItemPageData }
import uk.gov.hmrc.eeittadminfrontend.translation.TranslationAuditId
import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse }
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.eeittadminfrontend.translation.TranslationAuditOverview
import uk.gov.hmrc.http.HttpReads.Implicits._

import scala.concurrent.{ ExecutionContext, Future }

class GformConnector @Inject() (wsHttp: HttpClientV2, sc: ServicesConfig) {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  val gformUrl = s"${sc.baseUrl("gform")}/gform"

  def getGformsTemplate(
    formTemplateId: FormTemplateId
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[String, JsValue]] = {
    val url = url"$gformUrl/formtemplates/$formTemplateId/sensitive"
    wsHttp
      .get(url)
      .execute[HttpResponse]
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
    wsHttp
      .get(url"$gformUrl/submissionDetails/all/${formTemplateId.value}/$page/$pageSize")
      .execute[SubmissionPageData]

  def getAllGformsTemplates(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[JsValue] =
    wsHttp
      .get(url"$gformUrl/formtemplates")
      .execute[JsArray]

  def getAllSchema(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[JsValue] =
    wsHttp
      .get(url"/schemas")
      .execute[JsValue]

  def saveTemplate(
    formTemplateId: FormTemplateId,
    gformTemplate: JsValue
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[String, Unit]] =
    wsHttp
      .post(url"$gformUrl/formtemplates")
      .withBody(gformTemplate)
      .execute[HttpResponse]
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
      .delete(url"$gformUrl/formtemplates/$formTemplateId/sensitive")
      .execute[DeleteResults]

  def saveDBLookupIds(collectionName: String, dbLookupIds: Seq[DbLookupId])(implicit
    headerCarrier: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Unit] =
    wsHttp
      .put(url"$gformUrl/dblookup/$collectionName")
      .withBody(Json.toJson(dbLookupIds))
      .execute[HttpResponse]
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
  )(implicit headerCarrier: HeaderCarrier, ec: ExecutionContext): Future[PIIDetailsResponse] = {

    val queryParams = Seq(
      "filters"     -> filters.mkString(","),
      "includeJson" -> includeJson
    )

    val url = url"$gformUrl/formtemplates/get-titles-with-pii/${formTemplateRawId.value}?$queryParams"

    wsHttp
      .get(url)
      .execute[PIIDetailsResponse]
  }

  def getAllSavedVersions(implicit headerCarrier: HeaderCarrier, ec: ExecutionContext): Future[AllSavedVersions] =
    wsHttp
      .get(url"$gformUrl/formStatistics/all-saved-versions")
      .execute[AllSavedVersions]

  def getFormCount(
    formTemplateId: FormTemplateId
  )(implicit headerCarrier: HeaderCarrier, ec: ExecutionContext): Future[Seq[VersionStats]] =
    wsHttp
      .get(url"$gformUrl/formStatistics/${formTemplateId.value}")
      .execute[Seq[VersionStats]]

  def getFormDetailCount(
    formTemplateId: FormTemplateId
  )(implicit headerCarrier: HeaderCarrier, ec: ExecutionContext): Future[Seq[SavedFormDetail]] =
    wsHttp
      .get(url"$gformUrl/formStatistics/${formTemplateId.value}/details")
      .execute[Seq[SavedFormDetail]]

  def getSignedFormsDetails(implicit
    headerCarrier: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Seq[SignedFormDetails]] =
    wsHttp
      .get(url"$gformUrl/formStatistics/signed-forms")
      .execute[Seq[SignedFormDetails]]

  def deleteForm(
    formId: FormId
  )(implicit headerCarrier: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    wsHttp
      .post(url"$gformUrl/forms/${formId.value}/delete")
      .execute[HttpResponse]

  def unstuckForm(
    formId: FormId
  )(implicit headerCarrier: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    wsHttp
      .post(url"$gformUrl/forms/${formId.value}/unstuck")
      .execute[HttpResponse]

  def getSdesSubmissions(sdesFilter: SdesFilter)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[SdesSubmissionPageData] =
    wsHttp
      .post(url"$gformUrl/sdes/search")
      .withBody(Json.toJson(sdesFilter))
      .execute[SdesSubmissionPageData]

  def getSdesSubmission(correlationId: CorrelationId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ) =
    wsHttp
      .get(url"$gformUrl/sdes/${correlationId.value}")
      .execute[SdesSubmissionData]

  def getSdesSubmissionsByEnvelopeId(envelopeId: EnvelopeId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[List[SdesSubmission]] =
    wsHttp
      .get(url"$gformUrl/sdes/envelopeId/${envelopeId.value}")
      .execute[HttpResponse]
      .map { response =>
        if (response.status == 200) {
          response.json.validate[List[SdesSubmission]] match {
            case JsSuccess(list, _) =>
              list.sortWith((x, y) => if (x.destination === Some(Dms)) true else y.destination =!= Some(Dms))
            case JsError(_) =>
              response.json.validate[SdesSubmission] match {
                case JsSuccess(sub, _) => List(sub)
                case JsError(_) =>
                  logger.error(s"Unable to parse SdesSubmissions for envelopeId ${envelopeId.value}")
                  List.empty[SdesSubmission]
              }
          }
        } else {
          logger.error(
            s"Unable to retrieve SdesSubmissions for envelopeId ${envelopeId.value}, status = ${response.status}, body = '${response.body}'"
          )
          List.empty[SdesSubmission]
        }
      }
      .recover { case ex =>
        val message =
          s"Unknown problem when trying to retrieve SdesSubmissions for envelopeId ${envelopeId.value}, exception: " + ex.getMessage
        logger.error(message, ex)
        List.empty[SdesSubmission]
      }

  def notifySDES(correlationId: CorrelationId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    wsHttp
      .post(url"$gformUrl/sdes/notify/${correlationId.value}")
      .execute[HttpResponse]

  def resend(correlationId: CorrelationId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    wsHttp
      .post(url"$gformUrl/sdes/resend/${correlationId.value}")
      .execute[HttpResponse]

  def updateAsManualConfirmed(
    correlationId: CorrelationId
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    wsHttp
      .put(url"$gformUrl/sdes/${correlationId.value}")
      .execute[HttpResponse]

  def searchWorkItem(
    destination: SdesDestination,
    page: Int,
    pageSize: Int,
    formTemplateId: Option[FormTemplateId],
    status: Option[ProcessingStatus]
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[SdesWorkItemPageData] = {
    val queryParams = Seq(
      "formTemplateId" -> formTemplateId.map(_.value),
      "status"         -> status.map(_.name),
      "destination"    -> Some(destination.toString)
    ).collect { case (name, Some(value)) =>
      name -> value
    }
    wsHttp
      .get(url"$gformUrl/destination-work-item/search/$page/$pageSize?$queryParams")
      .execute[SdesWorkItemPageData]
  }

  def getWorkItem(destination: SdesDestination, id: String)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[SdesWorkItemData] =
    wsHttp
      .get(url"$gformUrl/destination-work-item/$id?destination=${destination.toString}")
      .execute[SdesWorkItemData]

  def enqueueWorkItem(destination: SdesDestination, id: String)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[HttpResponse] =
    wsHttp
      .post(url"$gformUrl/destination-work-item/enqueue/$id?destination=${destination.toString}")
      .execute[HttpResponse]

  def deleteWorkItem(destination: SdesDestination, id: String)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[HttpResponse] =
    wsHttp
      .delete(url"$gformUrl/destination-work-item/$id?destination=${destination.toString}")
      .execute[HttpResponse]

  def findNotificationBanner()(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[List[GformNotificationBannerView]] =
    wsHttp
      .get(url"$gformUrl/notification-banner")
      .execute[HttpResponse]
      .map { response =>
        if (response.status == 200) response.json.as[List[GformNotificationBannerView]]
        else List.empty[GformNotificationBannerView]
      }

  def deleteNotificationBanner(bannerId: BannerId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Unit] =
    wsHttp
      .delete(url"$gformUrl/notification-banner/${bannerId.value}")
      .execute[HttpResponse]
      .map(_ => ())

  def saveNotificationBanner(notificationBanner: GformNotificationBanner)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Unit] =
    wsHttp
      .post(url"$gformUrl/notification-banner")
      .withBody(Json.toJson(notificationBanner))
      .execute[HttpResponse]
      .map(_ => ())

  def saveNotificationBannerFormTemplates(notificationBannerFormTemplate: GformNotificationBannerFormTemplate)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Unit] =
    wsHttp
      .post(url"$gformUrl/notification-banner-form-template")
      .withBody(Json.toJson(notificationBannerFormTemplate))
      .execute[GformNotificationBannerFormTemplate]
      .map(_ => ())

  def deleteNotificationBannerFormTemplate(formTemplateId: FormTemplateId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Unit] =
    wsHttp
      .delete(url"$gformUrl/notification-banner-form-template/${formTemplateId.value}")
      .execute[HttpResponse]
      .map(_ => ())

  def findShutter()(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[List[ShutterView]] =
    wsHttp
      .get(url"$gformUrl/shutter")
      .execute[HttpResponse]
      .map { response =>
        if (response.status == 200) response.json.as[List[ShutterView]]
        else List.empty[ShutterView]
      }

  def deleteShutter(shutterMessageId: ShutterMessageId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Unit] =
    wsHttp
      .delete(url"$gformUrl/shutter/${shutterMessageId.value}")
      .execute[HttpResponse]
      .map(_ => ())

  def saveShutter(shutter: Shutter)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Unit] =
    wsHttp
      .post(url"$gformUrl/shutter")
      .withBody(Json.toJson(shutter))
      .execute[HttpResponse]
      .map(_ => ())

  def saveShutterFormTemplates(shutterFormTemplate: ShutterFormTemplate)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Unit] =
    wsHttp
      .post(url"$gformUrl/shutter-form-template")
      .withBody(Json.toJson(shutterFormTemplate))
      .execute[HttpResponse]
      .map(_ => ())

  def deleteShutterFormTemplate(formTemplateId: FormTemplateId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Unit] =
    wsHttp
      .delete(url"$gformUrl/shutter-form-template/${formTemplateId.value}")
      .execute[HttpResponse]
      .map(_ => ())

  def getFormTemplatesRedirects(page: Int, pageSize: Int)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[FormRedirectPageData] =
    wsHttp
      .get(url"$gformUrl/formtemplates-redirects/$page/$pageSize")
      .execute[FormRedirectPageData]

  def getHandlebarsTemplate(
    formTemplateId: FormTemplateId
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[String, String]] = {
    val url = url"$gformUrl/handlebarstemplates/${formTemplateId.value}"
    wsHttp
      .get(url)
      .setHeader(("Content-Type" -> "text/plain;charset=UTF-8"))
      .execute[HttpResponse]
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
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[String, String]] = {
    val url = url"$gformUrl/handlebarstemplates/${formTemplateId.value}/raw"
    wsHttp
      .get(url)
      .setHeader(("Content-Type" -> "text/plain;charset=UTF-8"))
      .execute[HttpResponse]
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
    wsHttp
      .delete(url"$gformUrl/handlebarstemplates/${formTemplateId.value}")
      .execute[DeleteResult]

  def saveHandlebarsTemplate(formTemplateId: FormTemplateId, payload: String)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Either[String, Unit]] =
    wsHttp
      .post(
        url"$gformUrl/handlebarstemplates/${formTemplateId.value}"
      )
      .withBody(payload)
      .setHeader(("Content-Type" -> "text/plain;charset=UTF-8"))
      .execute[HttpResponse]
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
    wsHttp
      .get(url"$gformUrl/handlebarstemplates")
      .execute[JsArray]

  def getHandlebarsTemplateIds(
    formTemplateId: FormTemplateId
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[JsValue] =
    wsHttp
      .get(url"$gformUrl/formtemplates-with-handlebars/$formTemplateId")
      .execute[JsArray]

  def getAllHandlebarsSchemas(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[JsValue] =
    wsHttp
      .get(url"$gformUrl/handlebars-schemas")
      .execute[JsArray]

  def getHandlebarsSchema(
    formTemplateId: FormTemplateId
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[String, HandlebarsSchema]] = {
    val url = url"$gformUrl/handlebars-schemas/${formTemplateId.value}"
    wsHttp
      .get(url)
      .execute[HttpResponse]
      .map { response =>
        if (response.status == 200) Right(response.json.as[HandlebarsSchema])
        else Left(s"Handlebars schema ${formTemplateId.value} not found")
      }
      .recover { case ex =>
        Left(s"Unknown problem when trying to retrieve handlebars schema $formTemplateId: " + ex.getMessage)
      }
  }

  def saveHandlebarsSchema(formTemplateId: FormTemplateId, schema: JsValue)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Either[String, Unit]] =
    wsHttp
      .post(url"$gformUrl/handlebars-schemas/${formTemplateId.value}")
      .withBody(schema)
      .execute[HttpResponse]
      .map { response =>
        if (response.status == 204)
          Right(())
        else
          Left((Json.parse(response.body) \ "error").as[String])
      }
      .recover { case ex =>
        val message = s"Unknown problem when trying to save handlebars schema $formTemplateId: " + ex.getMessage
        logger.error(message, ex)
        Left(message)
      }

  def deleteHandlebarsSchema(
    formTemplateId: FormTemplateId
  )(implicit headerCarrier: HeaderCarrier, ec: ExecutionContext): Future[DeleteResult] =
    wsHttp
      .delete(url"$gformUrl/handlebars-schemas/${formTemplateId.value}")
      .execute[DeleteResult]

  def getEnvelopeById(
    envelopeId: EnvelopeId
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[String, JsValue]] = {
    val url = url"$gformUrl/envelopes/${envelopeId.value}"
    wsHttp
      .get(url)
      .execute[HttpResponse]
      .map { response =>
        if (response.status == 200) Right(response.json) else Left(s"Envelope ${envelopeId.value} not found")
      }
      .recover { case ex =>
        Left(s"Unknown problem when trying to retrieve envelopeId $envelopeId: " + ex.getMessage)
      }
  }

  def getRetrievalsForEnvelopeId(
    envelopeId: EnvelopeId
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[String, JsValue]] = {
    val url = url"$gformUrl/retrieval/${envelopeId.value}"
    wsHttp
      .get(url)
      .execute[HttpResponse]
      .map { response =>
        if (response.status == 200) Right(response.json)
        else Left(s"Authenticated user retrievals for envelope '${envelopeId.value}' not found")
      }
      .recover { case ex =>
        Left(s"Unknown problem when trying to retrieve retrievals for envelopeId $envelopeId: " + ex.getMessage)
      }
  }

  def getFormDataForEnvelopeId(
    envelopeId: EnvelopeId
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[String, JsValue]] = {
    val url = url"$gformUrl/forms/envelope/${envelopeId.value}"
    wsHttp
      .get(url)
      .execute[HttpResponse]
      .map { response =>
        if (response.status == 200) Right(response.json)
        else Left(s"Form data for envelope '${envelopeId.value}' not found")
      }
      .recover { case ex =>
        Left(s"Unknown problem when trying to retrieve form data for envelopeId $envelopeId: " + ex.getMessage)
      }
  }

  def historyAllTemplateIds(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[List[FormTemplateRawId]] =
    wsHttp
      .get(url"$gformUrl/history-all-ids")
      .execute[List[String]]
      .map(_.map(FormTemplateRawId(_)))

  def historyOverviewForTemplateId(
    formTemplateRawId: FormTemplateRawId
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[List[HistoryOverview]] =
    wsHttp
      .get(url"$gformUrl/history/overview/${formTemplateRawId.value}")
      .execute[List[HistoryOverview]]

  def historyTemplate(historyId: HistoryId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[FormTemplateRaw] =
    wsHttp
      .get(url"$gformUrl/history/template/${historyId.value}")
      .execute[JsValue]
      .map { json =>
        FormTemplateRaw(CircePlayHelpers.playToCirceUnsafe(json))
      }

  def previousHistoryId(
    formTemplateRawId: FormTemplateRawId,
    historyId: HistoryId
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[HistoryId]] =
    wsHttp
      .get(
        url"$gformUrl/history/previous/${formTemplateRawId.value}/${historyId.value}"
      )
      .execute[Option[HistoryId]]

  def nextHistoryId(
    formTemplateRawId: FormTemplateRawId,
    historyId: HistoryId
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[HistoryId]] =
    wsHttp
      .get(
        url"$gformUrl/history/next/${formTemplateRawId.value}/${historyId.value}"
      )
      .execute[Option[HistoryId]]

  def historyWithFilter(
    historyFilter: HistoryFilter
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[List[HistoryOverviewFull]] =
    wsHttp
      .post(
        url"$gformUrl/history/filter"
      )
      .withBody(Json.toJson(historyFilter))
      .execute[List[HistoryOverviewFull]]

  def sdesDestinationsStats()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[SdesSubmissionsStats]] =
    wsHttp
      .get(
        url"$gformUrl/sdes/submissions/all-destinations"
      )
      .execute[Seq[SdesSubmissionsStats]]

  def runSdesMigration()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[String, String]] =
    wsHttp
      .post(
        url"$gformUrl/sdes/submissions/migration/DataStore/DataStoreLegacy"
      )
      .execute[HttpResponse]
      .map { response =>
        response.status match {
          case 200 => Right(response.body)
          case 400 => Left(response.body)
          case unknown =>
            throw new Exception(
              s"Wrong status code $unknown when running sdes submissions migration. Response body ${response.body}"
            )
        }
      }

  def rollbackSdesMigration()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[String, String]] =
    wsHttp
      .post(
        url"$gformUrl/sdes/submissions/migration/DataStoreLegacy/DataStore"
      )
      .execute[HttpResponse]
      .map { response =>
        response.status match {
          case 200 => Right(response.body)
          case 400 => Left(response.body)
          case unknown =>
            throw new Exception(
              s"Wrong status code $unknown when running rollback of sdes submissions migration. Response body ${response.body}"
            )
        }
      }

  def getSdesHistoryById(
    correlationId: CorrelationId
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[String, SdesHistoryView]] = {
    val url = url"$gformUrl/sdes/history/${correlationId.value}"
    wsHttp
      .get(url)
      .execute[HttpResponse]
      .map { response =>
        if (response.status == 200) Right(response.json.as[SdesHistoryView])
        else Left(s"Correlation ${correlationId.value} not found in event history")
      }
      .recover { case ex =>
        Left(s"Unknown problem when trying to retrieve correlationId $correlationId: " + ex.getMessage)
      }
  }

  def translationAuditOverview()(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[List[TranslationAuditOverview]] =
    wsHttp
      .get(url"$gformUrl/translation-audit/overview-all/")
      .execute[List[TranslationAuditOverview]]

  def translationAuditDownload(translationAuditId: TranslationAuditId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[HttpResponse] =
    wsHttp
      .get(url"$gformUrl/translation-audit/full/${translationAuditId.id}")
      .stream[HttpResponse]

  def downloadEnvelope(envelopeId: EnvelopeId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[HttpResponse] = downloadEnvelope(envelopeId, None)

  def downloadEnvelope(envelopeId: EnvelopeId, submissionPrefix: Option[String])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[HttpResponse] = {
    val queryParams = submissionPrefix.fold(Seq.empty[(String, String)])(p => Seq("prefix" -> p))
    wsHttp
      .get(url"$gformUrl/object-store/dms/envelopes/${envelopeId.value}?$queryParams")
      .stream[HttpResponse]
  }

  def downloadDataStore(envelopeId: EnvelopeId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[HttpResponse] =
    wsHttp
      .get(url"$gformUrl/object-store/data-store/envelopes/${envelopeId.value}")
      .stream[HttpResponse]

  def downloadHmrcIlluminate(envelopeId: EnvelopeId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[HttpResponse] =
    wsHttp
      .get(url"$gformUrl/object-store/hmrc-illuminate/envelopes/${envelopeId.value}")
      .stream[HttpResponse]

  def saveLog(
    log: CustomerDataAccessLog
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[String, Int]] =
    wsHttp
      .post(url"$gformUrl/access-logs")
      .withBody(Json.toJson(log))
      .execute[HttpResponse]
      .map { response =>
        response.status match {
          case 204 => Right(204)
          case 400 => Left(response.body)
          case unknown =>
            throw new Exception(
              s"Wrong status code $unknown when posting data access log. Response body ${response.body}"
            )
        }
      }

}
