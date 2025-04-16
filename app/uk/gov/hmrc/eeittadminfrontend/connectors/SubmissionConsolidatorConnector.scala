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

import uk.gov.hmrc.eeittadminfrontend.models.sdes.{ CorrelationId, NotificationStatus, SdesReportData, SdesReportsPageData }
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.HttpReads.Implicits._

import scala.concurrent.{ ExecutionContext, Future }

class SubmissionConsolidatorConnector @Inject() (wsHttp: HttpClientV2, sc: ServicesConfig) {

  val submissionConsolidatorUrl = s"${sc.baseUrl("submission-consolidator")}/submission-consolidator"

  val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  def consolidate(consolidatorJobId: String, startDate: LocalDate, endDate: LocalDate)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Either[String, Unit]] =
    wsHttp
      .post(
        url"$submissionConsolidatorUrl/consolidate/$consolidatorJobId/${startDate.format(DATE_FORMAT)}/${endDate.format(DATE_FORMAT)}"
      )
      .execute[HttpResponse]
      .map { response =>
        if (response.status == 204) // Results.NoContent
          Right(())
        else
          Left(response.toString)
      }
      .recover { case ex =>
        Left(
          s"Unknown problem when trying consolidate forms [consolidatorJobId=$consolidatorJobId, startDate=$startDate, endDate=$endDate]" + ex.getMessage
        )
      }

  def getSdesSubmissions(
    page: Int,
    pageSize: Int,
    maybeProcessed: Option[Boolean] = None,
    status: Option[NotificationStatus],
    showBeforeAt: Option[Boolean]
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[SdesReportsPageData] = {

    val queryParams: Seq[(String, String)] = Seq(
      "processed"    -> maybeProcessed.map(_.toString),
      "status"       -> status.map(_.toString),
      "showBeforeAt" -> showBeforeAt.map(_.toString)
    ).collect { case (k, Some(v)) =>
      k -> v
    }

    val url = url"$submissionConsolidatorUrl/sdes/search/$page/$pageSize?$queryParams"
    wsHttp
      .get(url)
      .execute[SdesReportsPageData]
  }

  def getSdesSubmission(correlationId: CorrelationId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[SdesReportData] =
    wsHttp
      .get(url"$submissionConsolidatorUrl/sdes/${correlationId.value}")
      .execute[SdesReportData]

  def updateAsManualConfirmed(
    correlationId: CorrelationId
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    wsHttp.put(url"$submissionConsolidatorUrl/sdes/${correlationId.value}").execute[HttpResponse]

  def notifySDES(correlationId: CorrelationId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    wsHttp
      .post(url"$submissionConsolidatorUrl/sdes/notify/${correlationId.value}")
      .execute[HttpResponse]

}
