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

import org.mockito.Mockito.when
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.eeittadminfrontend.connectors.SubmissionConsolidatorConnector
import uk.gov.hmrc.eeittadminfrontend.models.{ DmsReport, DmsReportData }
import uk.gov.hmrc.eeittadminfrontend.models.fileupload.EnvelopeId
import uk.gov.hmrc.eeittadminfrontend.models.sdes._
import uk.gov.hmrc.http.HttpResponse

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DmsSubReconciliationServiceSpec extends AnyWordSpecLike with Matchers with OptionValues with MockitoSugar {

  val subConConnector: SubmissionConsolidatorConnector = mock[SubmissionConsolidatorConnector]
  val dmsSubReconciliationService = new DmsSubReconciliationService(subConConnector)

  "sdesToBeReconciled" should {
    "return sdes subs to be manually processed" when {
      val sub1 = SdesReportData(
        None,
        None,
        None,
        CorrelationId("correlationId"),
        EnvelopeId("envelopeId"),
        SubmissionRef("submissionRef"),
        None,
        NotificationStatus.FileReady,
        "failed",
        None
      )
      val sub2 = sub1.copy(
        correlationId = CorrelationId("correlationId2"),
        submissionRef = SubmissionRef("submissionRef2"),
        envelopeId = EnvelopeId("envelopeId2"),
        status = NotificationStatus.FileProcessingFailure
      )
      val sub3 = sub1.copy(
        correlationId = CorrelationId("correlationId3"),
        submissionRef = SubmissionRef("submissionRef3"),
        envelopeId = EnvelopeId("envelopeId3"),
        status = NotificationStatus.FileProcessed //this sub is in both but is already processed
      )
      val sub4 = sub1.copy(
        //this sub is not in the dms report,
        //therefore is not returned as manually processed even the status is FileReady
        correlationId = CorrelationId("correlationId4"),
        envelopeId = EnvelopeId("envelopeId4"),
        submissionRef = SubmissionRef("submissionRef4")
      )
      val sub5 = sub1.copy(
        correlationId = CorrelationId("correlationId5"),
        envelopeId = EnvelopeId("envelopeId5"),
        submissionRef = SubmissionRef("submissionRef5"),
        status = NotificationStatus.FileProcessedManualConfirmed //this sub is in both but is already processed
      )

      val sdesReportsPageData: SdesReportsPageData = SdesReportsPageData(List(sub1, sub2, sub3, sub4, sub5), 5)

      val dmsReportData: List[DmsReportData] = List(
        DmsReportData(SubmissionRef("submissionRef"), EnvelopeId("envelopeId")),
        DmsReportData(SubmissionRef("submissionRef2"), EnvelopeId("envelopeId2")),
        DmsReportData(
          SubmissionRef("submissionRef3"),
          EnvelopeId("envelopeId3")
        ), //this sub is in both but is already processed
        DmsReportData(
          SubmissionRef("submissionRef5"),
          EnvelopeId("envelopeId5")
        ) //this sub is in both but is already processed
      )
      val dmsReport: DmsReport = DmsReport(dmsReportData, 4)

      "DMS says that the subs have been received" in {
        dmsSubReconciliationService.sdesToBeReconciled(sdesReportsPageData, dmsReport) shouldBe List(sub1, sub2)
      }
    }
  }

  "sdesReconcile" should {
    "return the reconciled subs" when {
      val sub1 = SdesReportData(
        None,
        None,
        None,
        CorrelationId("correlationId"),
        EnvelopeId("envelopeId"),
        SubmissionRef("submissionRef"),
        None,
        NotificationStatus.FileReady,
        "failed",
        None
      )
      val sub2 = sub1.copy(
        correlationId = CorrelationId("correlationId2"),
        submissionRef = SubmissionRef("submissionRef2"),
        envelopeId = EnvelopeId("envelopeId2"),
        status = NotificationStatus.FileProcessingFailure
      )

      "the subs have been marked as manually processed" in {
        when(subConConnector.updateAsManualConfirmed(sub1.correlationId))
          .thenReturn(Future.successful(HttpResponse(200, "")))
        when(subConConnector.updateAsManualConfirmed(sub2.correlationId))
          .thenReturn(Future.successful(HttpResponse(200, "")))

        val result = dmsSubReconciliationService.sdesReconcile(List(sub1, sub2)).futureValue

        result shouldBe SdesReportsPageData(List(sub1, sub2), 2)
      }
    }
  }
}
