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

package uk.gov.hmrc.eeittadminfrontend.models.sdes

import cats.Eq
import play.api.libs.json._
import uk.gov.hmrc.eeittadminfrontend.models.fileupload.EnvelopeId
import uk.gov.hmrc.eeittadminfrontend.models.{ FormTemplateId, ValueClassFormatter }

import java.time.Instant

case class SdesSubmissionPageData(
  sdesSubmissions: List[SdesSubmissionData],
  count: Long
)

object SdesSubmissionPageData {
  implicit val format: OFormat[SdesSubmissionPageData] = Json.format[SdesSubmissionPageData]
}

case class SdesSubmissionData(
  correlationId: CorrelationId,
  envelopeId: EnvelopeId,
  formTemplateId: FormTemplateId,
  submissionRef: SubmissionRef,
  numberOfFiles: Int,
  uploadCount: Int,
  size: Long,
  submittedAt: Option[Instant],
  status: NotificationStatus,
  failureReason: String,
  lastUpdated: Option[Instant],
  destination: SdesDestination
)

object SdesSubmissionData {
  implicit val format: OFormat[SdesSubmissionData] = Json.format[SdesSubmissionData]
}

case class SubmissionRef(value: String) extends AnyVal

object SubmissionRef {
  implicit val format: Format[SubmissionRef] = ValueClassFormatter.format(SubmissionRef.apply)(_.value)
}

case class CorrelationId(value: String) extends AnyVal

object CorrelationId {
  implicit val format: Format[CorrelationId] = ValueClassFormatter.format(CorrelationId.apply)(_.value)
}

sealed trait NotificationStatus extends Product with Serializable

object NotificationStatus {

  case object FileReady extends NotificationStatus

  case object FileReceived extends NotificationStatus

  case object FileProcessingFailure extends NotificationStatus

  case object FileProcessed extends NotificationStatus
  case object FileProcessedManualConfirmed extends NotificationStatus

  val values: Set[NotificationStatus] =
    Set(
      FileReady,
      FileReceived,
      FileProcessingFailure,
      FileProcessed,
      FileProcessedManualConfirmed
    )

  val updatableStatuses: Set[NotificationStatus] =
    Set(
      FileReady,
      FileReceived,
      FileProcessingFailure
    )

  val notifiableStatuses: Set[NotificationStatus] =
    Set(
      FileReady,
      FileReceived,
      FileProcessingFailure
    )

  implicit val catsEq: Eq[NotificationStatus] = Eq.fromUniversalEquals

  implicit val format: Format[NotificationStatus] = new Format[NotificationStatus] {
    override def writes(o: NotificationStatus): JsValue = o match {
      case FileReady                    => JsString("FileReady")
      case FileReceived                 => JsString("FileReceived")
      case FileProcessingFailure        => JsString("FileProcessingFailure")
      case FileProcessed                => JsString("FileProcessed")
      case FileProcessedManualConfirmed => JsString("FileProcessedManualConfirmed")
    }

    override def reads(json: JsValue): JsResult[NotificationStatus] =
      json match {
        case JsString("FileReady")                    => JsSuccess(FileReady)
        case JsString("FileReceived")                 => JsSuccess(FileReceived)
        case JsString("FileProcessingFailure")        => JsSuccess(FileProcessingFailure)
        case JsString("FileProcessed")                => JsSuccess(FileProcessed)
        case JsString("FileProcessedManualConfirmed") => JsSuccess(FileProcessedManualConfirmed)
        case JsString(err) =>
          JsError(s"only for valid FileReady, FileReceived, FileProcessingFailure or FileProcessed.$err is not allowed")
        case _ => JsError("Failure")
      }
  }

  def fromName(notificationStatus: NotificationStatus): String = notificationStatus match {
    case FileReady                    => "FileReady"
    case FileReceived                 => "FileReceived"
    case FileProcessingFailure        => "FileProcessingFailure"
    case FileProcessed                => "FileProcessed"
    case FileProcessedManualConfirmed => "FileProcessedManualConfirmed"
  }

  def fromString(notificationStatus: String): NotificationStatus = notificationStatus match {
    case "FileReady"                    => FileReady
    case "FileReceived"                 => FileReceived
    case "FileProcessingFailure"        => FileProcessingFailure
    case "FileProcessed"                => FileProcessed
    case "FileProcessedManualConfirmed" => FileProcessedManualConfirmed
  }
}

sealed trait SdesDestination extends Product with Serializable

object SdesDestination {
  case object Dms extends SdesDestination
  case object DataStore extends SdesDestination

  val values: Set[SdesDestination] = Set(Dms, DataStore)

  implicit val equal: Eq[SdesDestination] = Eq.fromUniversalEquals
  implicit val format: Format[SdesDestination] = new Format[SdesDestination] {
    override def writes(o: SdesDestination): JsValue = o match {
      case Dms       => JsString("Dms")
      case DataStore => JsString("DataStore")
    }

    override def reads(json: JsValue): JsResult[SdesDestination] =
      json match {
        case JsString("Dms")       => JsSuccess(Dms)
        case JsString("DataStore") => JsSuccess(DataStore)
        case JsString(err) =>
          JsError(s"only for valid Dms, or DataStore.$err is not allowed")
        case _ => JsError("Failure")
      }
  }

  def fromName(destination: SdesDestination): String = destination match {
    case Dms       => "Dms"
    case DataStore => "DataStore"
  }

  def fromString(destination: String): SdesDestination = destination match {
    case "Dms"       => Dms
    case "DataStore" => DataStore
  }
}
