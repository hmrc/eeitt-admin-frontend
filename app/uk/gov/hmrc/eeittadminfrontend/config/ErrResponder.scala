/*
 * Copyright 2021 HM Revenue & Customs
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
package config

import play.api.Logger
import play.api.i18n.{ I18nSupport, Messages, MessagesApi }
import play.api.mvc.Results.{ BadRequest, Forbidden, InternalServerError, NotFound }
import play.api.mvc.{ RequestHeader, Result }
import uk.gov.hmrc.eeittadminfrontend.auditing.HttpAuditingService

import scala.concurrent.Future

/** This object suppose to render responses if something unexpected happened.
  * It as well does a bunch of side effects:
  *  - sends audit events
  *  - does logging so please use it just before returning
  */
class ErrResponder(httpAuditingService: HttpAuditingService, i18nSupport: I18nSupport)(implicit
  val messagesApi: MessagesApi,
  frontendAppConfig: AppConfig
) {
  import i18nSupport._

  private val restricted = "We're sorry, but this page is restricted."

  def internalServerError(requestHeader: RequestHeader, e: Throwable) = {
    Logger.logger.error(s"Experienced internal server error", e)
    httpAuditingService.auditServerError(requestHeader)
    Future.successful(InternalServerError.apply(renderInternalServerError(requestHeader)))
  }

  def onOtherClientError(requestHeader: RequestHeader, statusCode: Int, message: String) = {
    Logger.logger.warn(s"Experienced internal server error, statusCode=$statusCode, message=$message")
    //no auditing
    Future.successful(InternalServerError.apply(renderInternalServerError(requestHeader)))
  }

  def forbidden(requestHeader: RequestHeader, message: String): Future[Result] =
    forbiddenReason(requestHeader, message, restricted)

  def forbiddenWithReason(requestHeader: RequestHeader, reason: String): Future[Result] =
    forbiddenReason(requestHeader, reason, reason)

  private def forbiddenReason(requestHeader: RequestHeader, message: String, reason: String): Future[Result] = {
    Logger.logger.info(s"Trying to access forbidden resource: $message")
    httpAuditingService.auditForbidden(requestHeader)
    Future.successful(Forbidden(renderForbidden(reason)(requestHeader)))
  }

  def badRequest(requestHeader: RequestHeader, message: String): Future[Result] = {
    Logger.logger.info(s"Bad request: $message")
    httpAuditingService.auditBadRequest(requestHeader, message)
    Future.successful(BadRequest(renderBadRequest(requestHeader)))
  }

  def notFound(requestHeader: RequestHeader, message: String): Future[Result] = {
    Logger.logger.info(s"Page NotFound: $message")
    httpAuditingService.auditNotFound(requestHeader)
    Future.successful(NotFound(renderNotFound(requestHeader)))
  }

  private def renderInternalServerError(implicit request: RequestHeader) =
    renderErrorPage(
      pageTitle = Messages("global.error.InternalServerError500.title"),
      heading = Messages("global.error.InternalServerError500.heading"),
      message = Messages("global.error.InternalServerError500.message")
    )

  private def renderForbidden(reason: String)(implicit request: RequestHeader) =
    renderErrorPage(
      "Access forbidden",
      restricted,
      reason
    )

  private def renderNotFound(implicit request: RequestHeader) =
    renderErrorPage(
      pageTitle = Messages("global.error.pageNotFound404.title"),
      heading = Messages("global.error.pageNotFound404.heading"),
      message = Messages("global.error.pageNotFound404.message")
    )

  private def renderBadRequest(implicit request: RequestHeader) =
    renderErrorPage(
      pageTitle = Messages("global.error.badRequest400.title"),
      heading = Messages("global.error.badRequest400.heading"),
      message = Messages("global.error.badRequest400.message")
    )

  private def renderErrorPage(
    pageTitle: String,
    heading: String,
    message: String
  )(implicit requestHeader: RequestHeader) =
    views.html.error_template(pageTitle, heading, message)
}
