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

import uk.gov.hmrc.eeittadminfrontend.connectors.GformConnector
import uk.gov.hmrc.eeittadminfrontend.models.DmsReport
import uk.gov.hmrc.eeittadminfrontend.models.sdes.{ NotificationStatus, SdesReconciliation, SdesReconciliationData, SdesSubmissionPageData }

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }
import uk.gov.hmrc.http.HeaderCarrier

class DmsSubReconciliationService @Inject() (gformConnector: GformConnector) {

  def sdesToBeReconciled(sdesReport: SdesSubmissionPageData, dmsReport: DmsReport): List[SdesReconciliationData] =
    filterSubsNotProcessedInDmsReport(subsNotProcessed(sdesReport), dmsReport)

  private def subsNotProcessed(sdesReport: SdesSubmissionPageData): SdesSubmissionPageData = sdesReport match {
    case SdesSubmissionPageData(subs, _) =>
      val subsToReconcile = subs.filter(sub =>
        sub.status != NotificationStatus.FileProcessed && sub.status != NotificationStatus.FileProcessedManualConfirmed
      )
      SdesSubmissionPageData(subsToReconcile, subsToReconcile.length.toLong)
  }

  private def filterSubsNotProcessedInDmsReport(
    subsNotProcessed: SdesSubmissionPageData,
    dmsReport: DmsReport
  ): List[SdesReconciliationData] =
    subsNotProcessed.sdesSubmissions
      .filter { sub =>
        dmsReport.data.exists(_.envelopeId == sub.envelopeId)
      }
      .map(sub => SdesReconciliationData(sub.correlationId))

  def sdesReconcile(
    reconcileData: List[SdesReconciliationData]
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[SdesReconciliation] =
    Future
      .sequence(reconcileData.map { sub =>
        gformConnector
          .updateAsManualConfirmed(sub.correlationId)
          .map { res =>
            if (res.status >= 200 && res.status < 300) Some(sub)
            else None
          }
          .collect { case Some(reconciledSub) => reconciledSub }
      })
      .map { reconciledSubs =>
        SdesReconciliation(reconciledSubs, reconciledSubs.length.toLong)
      }
}
