/*
 * Copyright 2022 HM Revenue & Customs
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

import java.time.LocalDate
import play.api.data.Form
import play.api.data.Forms.{ mapping, nonEmptyText }
import play.api.i18n.I18nSupport
import play.api.mvc.MessagesControllerComponents
import uk.gov.hmrc.eeittadminfrontend.auth.AuthConnector
import uk.gov.hmrc.eeittadminfrontend.config.{ AppConfig, AuthAction }
import uk.gov.hmrc.eeittadminfrontend.connectors.SubmissionConsolidatorConnector
import uk.gov.hmrc.eeittadminfrontend.models.submissionconsolidator.ManualConsolidationForm
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.{ ExecutionContext, Future }

class SubmissionConsolidatorController(
  val authConnector: AuthConnector,
  authAction: AuthAction,
  submissionConsolidatorConnector: SubmissionConsolidatorConnector,
  messagesControllerComponents: MessagesControllerComponents
)(implicit ec: ExecutionContext, appConfig: AppConfig)
    extends FrontendController(messagesControllerComponents) with I18nSupport {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  private val manualConsolidationForm: Form[ManualConsolidationForm] = Form(
    mapping("consolidatorJobId" -> nonEmptyText, "startDate" -> nonEmptyText, "endDate" -> nonEmptyText)(
      ManualConsolidationForm.apply
    )(ManualConsolidationForm.unapply)
  )

  def submissionConsolidatorPage() =
    authAction.async { implicit request =>
      Future.successful(Ok(uk.gov.hmrc.eeittadminfrontend.views.html.submission_consolidator(manualConsolidationForm)))
    }

  def consolidate() =
    authAction.async { implicit request =>
      manualConsolidationForm
        .bindFromRequest()
        .fold(
          form => {
            logger.error(s"Failed to consolidate submissions $form")
            Future.successful(BadRequest(uk.gov.hmrc.eeittadminfrontend.views.html.submission_consolidator(form)))
          },
          form => {
            logger.info(
              s"User ${request.userData} triggered consolidaton [consolidatorJobId=${form.consolidatorJobId}, startDate=${form.startDate}, endDate=${form.endDate}]"
            )
            submissionConsolidatorConnector
              .consolidate(form.consolidatorJobId, LocalDate.parse(form.startDate), LocalDate.parse(form.endDate))
              .map {
                case Right(_)    => Ok("Consolidation complete")
                case Left(error) => InternalServerError(error)
              }
          }
        )
    }
}
