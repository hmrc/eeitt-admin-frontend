/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.eeittadminfrontend.services

import uk.gov.hmrc.eeittadminfrontend.connectors.SubmissionConsolidatorConnector
import uk.gov.hmrc.eeittadminfrontend.models.DmsReport
import uk.gov.hmrc.eeittadminfrontend.models.sdes.{ NotificationStatus, SdesReportData, SdesReportsPageData }

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class DmsSubReconciliationService @Inject() (subConConnector: SubmissionConsolidatorConnector) {

  def sdesToBeReconciled(sdesReport: SdesReportsPageData, dmsReport: DmsReport): List[SdesReportData] =
    filterSubsNotProcessedInDmsReport(subsNotProcessed(sdesReport), dmsReport)

  private def subsNotProcessed(sdesReport: SdesReportsPageData): SdesReportsPageData = sdesReport match {
    case SdesReportsPageData(subs, _) =>
      val subsToReconcile = subs.filter(sub =>
        sub.status != NotificationStatus.FileProcessed && sub.status != NotificationStatus.FileProcessedManualConfirmed
      )
      SdesReportsPageData(subsToReconcile, subsToReconcile.length.toLong)
  }

  private def filterSubsNotProcessedInDmsReport(
    subsNotProcessed: SdesReportsPageData,
    dmsReport: DmsReport
  ): List[SdesReportData] =
    subsNotProcessed.sdesSubmissions.filter { sub =>
      dmsReport.data.exists(_.envelopeId == sub.envelopeId)
    }

  def sdesReconcile(reconcileData: List[SdesReportData])(implicit ec: ExecutionContext): Future[SdesReportsPageData] =
    Future
      .sequence(reconcileData.map { sub =>
        subConConnector
          .updateAsManualConfirmed(sub.correlationId)
          .map { res =>
            if (res.status >= 200 && res.status < 300) Some(sub)
            else None
          }
          .collect { case Some(sub) => sub }
      })
      .map { reconciledSubs =>
        SdesReportsPageData(reconciledSubs, reconciledSubs.length.toLong)
      }
}
