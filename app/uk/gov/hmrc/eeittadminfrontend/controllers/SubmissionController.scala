/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.eeittadminfrontend.controllers

import java.time.{ LocalDateTime, ZoneId }
import play.api.Logger
import play.api.i18n.{ I18nSupport, MessagesApi }
import play.api.libs.json.{ JsArray, JsString }
import scala.concurrent.Future
import uk.gov.hmrc.eeittadminfrontend.AppConfig
import uk.gov.hmrc.eeittadminfrontend.config.Authentication
import uk.gov.hmrc.eeittadminfrontend.config.RequestWithUser._
import uk.gov.hmrc.eeittadminfrontend.connectors.{ FileUploadConnector, GformConnector }
import uk.gov.hmrc.eeittadminfrontend.models.{ AttachmentCheck, FormTypeId, Submission }
import uk.gov.hmrc.eeittadminfrontend.models.fileupload.{ Envelope, EnvelopeId }
import uk.gov.hmrc.play.frontend.auth.Actions
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.controller.FrontendController

class SubmissionController(val authConnector: AuthConnector)(
  implicit appConfig: AppConfig,
  val messagesApi: MessagesApi)
    extends FrontendController with Actions with I18nSupport {

  def submissions() = Authentication.async { implicit request =>
    GformConnector.getAllGformsTemplates.map { templates =>
      templates match {
        case JsArray(formTemplateIds) =>
          val ftIds: Seq[FormTypeId] = formTemplateIds.collect {
            case JsString(id) if !id.startsWith("specimen-") => FormTypeId(id)
          }
          Ok(uk.gov.hmrc.eeittadminfrontend.views.html.submissions(ftIds.sortBy(_.value)))
        case other => BadRequest("Cannot retrieve form templates. Expected JsArray, got: " + other)
      }
    }
  }

  implicit val localDateTimeOrdering: Ordering[LocalDateTime] =
    Ordering.by(_.atZone(ZoneId.of("UTC")).toInstant().toEpochMilli())

  def submission(formTemplateId: FormTypeId) = Authentication.async { implicit request =>
    Logger.info(s"${request.userLogin} looking at submissions for " + formTemplateId)
    GformConnector.getAllSubmissons(formTemplateId).flatMap {
      case jsonSubmissions =>
        val submissions = jsonSubmissions.as[List[Submission]].sortBy(_.submittedDate).reverse
        val submissionsWithCheck: Future[List[(Submission, Envelope, AttachmentCheck)]] = Future.traverse(submissions) {
          submission =>
            val envelopeId = EnvelopeId(submission.envelopeId)
            FileUploadConnector.getEnvelopeById(envelopeId).map {
              case Right(jsValue) =>
                jsValue.asOpt[Envelope] match {
                  case Some(envelope) =>
                    if (submission.attachment_count == (envelope.files.size - 2)) {
                      (submission, envelope, AttachmentCheck.CountOk)
                    } else {
                      (submission, envelope, AttachmentCheck.CountDoesNotMatch(envelope.files.size))
                    }
                  case None =>
                    Logger.warn(s"${request.userLogin} failed to parse envelopeId $envelopeId. Json payload: $jsValue")
                    (submission, Envelope.nonExistentEnvelope(envelopeId), AttachmentCheck.CannotParseEnvelope)
                }
              case Left(error) =>
                Logger.warn(s"${request.userLogin} failed to retrieve envelopeId $envelopeId. Error: $error")
                (submission, Envelope.nonExistentEnvelope(envelopeId), AttachmentCheck.EnvelopeDoesNotExists)
            }
        }

        submissionsWithCheck.map { data =>
          Ok(uk.gov.hmrc.eeittadminfrontend.views.html.submission(formTemplateId, data))
        }
    }
  }
}
