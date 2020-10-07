/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.eeittadminfrontend
package controllers

import java.time.{ LocalDateTime, ZoneId }

import play.api.Logger
import play.api.i18n.I18nSupport
import play.api.libs.json.{ JsArray, JsString }
import play.api.mvc.MessagesControllerComponents
import uk.gov.hmrc.eeittadminfrontend.auth.AuthConnector
import uk.gov.hmrc.eeittadminfrontend.config.{ AppConfig, AuthAction }
import uk.gov.hmrc.eeittadminfrontend.connectors.{ FileUploadConnector, GformConnector }
import uk.gov.hmrc.eeittadminfrontend.models.fileupload.{ Envelope, EnvelopeId }
import uk.gov.hmrc.eeittadminfrontend.models.{ AttachmentCheck, FormTemplateId, Submission }
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

import scala.concurrent.{ ExecutionContext, Future }

class SubmissionController(
  val authConnector: AuthConnector,
  authAction: AuthAction,
  gformConnector: GformConnector,
  fileUploadConnector: FileUploadConnector,
  messagesControllerComponents: MessagesControllerComponents)(implicit ec: ExecutionContext, appConfig: AppConfig)
    extends FrontendController(messagesControllerComponents) with I18nSupport {

  def submissions() = authAction.async { implicit request =>
    gformConnector.getAllGformsTemplates.map {
      case JsArray(formTemplateIds) =>
        val ftIds: Seq[FormTemplateId] = formTemplateIds.collect {
          case JsString(id) if !id.startsWith("specimen-") => FormTemplateId(id)
        }
        Ok(uk.gov.hmrc.eeittadminfrontend.views.html.submissions(ftIds.sortBy(_.value)))
      case other => BadRequest("Cannot retrieve form templates. Expected JsArray, got: " + other)
    }
  }

  implicit val localDateTimeOrdering: Ordering[LocalDateTime] =
    Ordering.by(_.atZone(ZoneId.of("UTC")).toInstant().toEpochMilli())

  def submission(formTemplateId: FormTemplateId) = authAction.async { implicit request =>
    Logger.info(s"${request.userLogin} looking at submissions for " + formTemplateId)
    gformConnector.getAllSubmissons(formTemplateId).flatMap {
      case jsonSubmissions =>
        val submissions = jsonSubmissions.as[List[Submission]].sortBy(_.submittedDate).reverse
        val submissionsWithCheck: Future[List[(Submission, Envelope, AttachmentCheck)]] = Future.traverse(submissions) {
          submission =>
            val envelopeId = EnvelopeId(submission.envelopeId)
            fileUploadConnector.getEnvelopeById(envelopeId).map {
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
