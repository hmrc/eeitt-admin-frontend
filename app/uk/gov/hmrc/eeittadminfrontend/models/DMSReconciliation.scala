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

package uk.gov.hmrc.eeittadminfrontend.models

import uk.gov.hmrc.eeittadminfrontend.models.fileupload.EnvelopeId
import uk.gov.hmrc.eeittadminfrontend.models.sdes.SubmissionRef

import java.io.File
import scala.io.Source
import scala.util.Using

case class DmsReport(data: List[DmsReportData], count: Int)
case class DmsReportData(submissionRef: SubmissionRef, envelopeId: EnvelopeId)

object DmsReport {
  def apply(file: File): DmsReport =
    Using(Source.fromFile(file)) { source =>
      val lines = source.getLines().toList

      val data = lines.map { line =>
        line.split(",") match {
          case Array(submissionRef, envelopeId) =>
            DmsReportData(SubmissionRef(submissionRef), EnvelopeId(envelopeId))
          case _ => throw new IllegalArgumentException(s"Invalid line: $line")
        }
      }
      DmsReport(data, data.length)
    }.getOrElse(DmsReport(List.empty, 0))
}
