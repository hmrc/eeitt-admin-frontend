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
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.http.{ HeaderCarrier, HttpClient, HttpResponse }
import uk.gov.hmrc.http.HttpReads.Implicits.readFromJson

import scala.concurrent.{ ExecutionContext, Future }

class SubmissionConsolidatorConnector @Inject() (wsHttp: HttpClient, sc: ServicesConfig) {
  val submissionConsolidatorUrl = s"${sc.baseUrl("submission-consolidator")}/submission-consolidator"

  val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  def consolidate(consolidatorJobId: String, startDate: LocalDate, endDate: LocalDate)(implicit
    ec: ExecutionContext
  ): Future[Either[String, Unit]] =
    wsHttp
      .doPost[String](
        s"$submissionConsolidatorUrl/consolidate/$consolidatorJobId/${startDate.format(DATE_FORMAT)}/${endDate.format(DATE_FORMAT)}",
        ""
      )
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
    val queryStringByProcessed = maybeProcessed.fold("")(p => s"processed=$p")
    val queryStringByStatus =
      status.fold(queryStringByProcessed)(status => s"status=$status&$queryStringByProcessed")
    val queryString =
      showBeforeAt.fold(queryStringByStatus)(showBeforeAt => s"showBeforeAt=$showBeforeAt&$queryStringByStatus")
    wsHttp.GET[SdesReportsPageData](s"$submissionConsolidatorUrl/sdes/search/$page/$pageSize?$queryString")
  }

  def getSdesSubmission(correlationId: CorrelationId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[SdesReportData] =
    wsHttp.GET[SdesReportData](s"$submissionConsolidatorUrl/sdes/${correlationId.value}")

  def deleteSdesSubmission(correlationId: CorrelationId)(implicit ec: ExecutionContext): Future[HttpResponse] =
    wsHttp.doDelete(s"$submissionConsolidatorUrl/sdes/${correlationId.value}")

  def notifySDES(correlationId: CorrelationId)(implicit ec: ExecutionContext): Future[HttpResponse] =
    wsHttp
      .doPost[String](s"$submissionConsolidatorUrl/sdes/notify/${correlationId.value}", "")

}
