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

package uk.gov.hmrc.eeittadminfrontend.config

import play.api.mvc.Results._
import play.api.mvc._

import scala.concurrent.{ ExecutionContext, Future }

case class RequestWithUser[A](request: Request[A], userLogin: String) extends WrappedRequest[A](request)

object RequestWithUser {
  implicit def asRequest[A](implicit rwu: RequestWithUser[A]): Request[A] = rwu.request
}

class AuthAction(messagesControllerComponents: MessagesControllerComponents)
    extends ActionBuilder[RequestWithUser, AnyContent] {

  override def executionContext: ExecutionContext = messagesControllerComponents.executionContext
  override def parser: BodyParser[AnyContent] = messagesControllerComponents.parsers.default

  private def username(request: RequestHeader): Option[String] = request.session.get("token")

  def onUnauthorised =
    Redirect(uk.gov.hmrc.eeittadminfrontend.controllers.routes.AuthController.loginPage())

  override def invokeBlock[A](request: Request[A], block: RequestWithUser[A] => Future[Result]): Future[Result] =
    username(request)
      .map { login =>
        block(RequestWithUser(request, login))
      }
      .getOrElse(Future.successful(onUnauthorised))
}
