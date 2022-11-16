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

import play.api.i18n.I18nSupport
import play.api.mvc.MessagesControllerComponents
import uk.gov.hmrc.eeittadminfrontend.connectors.GformConnector
import uk.gov.hmrc.eeittadminfrontend.models.Pagination
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

  def sdesSubmissions(page: Int) =
    authAction.async { implicit request =>
      gformConnector.getSdesSubmissions(false, page, pageSize).map { sdesSubmissionPageData =>
        val pagination = Pagination(sdesSubmissionPageData.count, page, sdesSubmissionPageData.count.toInt, pageSize)
        Ok(submission_sdes(pagination, sdesSubmissionPageData))
      }
    }
}
