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

package uk.gov.hmrc.eeittadminfrontend.controllers

import org.slf4j.{ Logger, LoggerFactory }
import play.api.i18n.I18nSupport
import play.api.mvc.MessagesControllerComponents
import uk.gov.hmrc.eeittadminfrontend.connectors.GformConnector
import uk.gov.hmrc.eeittadminfrontend.models.{ CorrelationId, Pagination, SubmissionRef }
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class SubmissionSdesController @Inject() (
  frontendAuthComponents: FrontendAuthComponents,
  gformConnector: GformConnector,
  messagesControllerComponents: MessagesControllerComponents,
  submission_sdes: uk.gov.hmrc.eeittadminfrontend.views.html.submission_sdes
)(implicit ec: ExecutionContext)
    extends GformAdminFrontendController(frontendAuthComponents, messagesControllerComponents) with I18nSupport {

  private val pageSize = 10
  private val logger: Logger = LoggerFactory.getLogger(getClass)

  def sdesSubmissions(page: Int) =
    authAction.async { implicit request =>
      gformConnector.getSdesSubmissions(false, page, pageSize).map { sdesSubmissionPageData =>
        val pagination = Pagination(sdesSubmissionPageData.count, page, sdesSubmissionPageData.count.toInt, pageSize)
        Ok(submission_sdes(pagination, sdesSubmissionPageData))
      }
    }

  def notifySDES(correlationId: CorrelationId, submissionRef: SubmissionRef, page: Int) =
    authAction.async { implicit request =>
      val username = request.retrieval
      logger.info(
        s"${username.value} sends a notification to SDES for correlation id ${correlationId.value}, submission id  ${submissionRef.value}"
      )
      gformConnector.notifySDES(correlationId).map { httpResponse =>
        Redirect(routes.SubmissionSdesController.sdesSubmissions(page))
          .flashing(
            "success" -> s"Envelope successfully notified. Correlation id: ${correlationId.value}, submission id: ${submissionRef.value}"
          )
      }
    }
}
