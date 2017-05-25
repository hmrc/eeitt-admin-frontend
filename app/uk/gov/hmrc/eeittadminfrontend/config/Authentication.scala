/*
 * Copyright 2017 HM Revenue & Customs
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

import scala.concurrent.Future

object Authentication extends ActionBuilder[Request]{

  def username(request: RequestHeader): Option[String] = request.session.get("token")

  def onUnauthorised(request: RequestHeader) = Redirect(uk.gov.hmrc.eeittadminfrontend.controllers.routes.AuthController.loginPage())

  override def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]): Future[Result] = {
      username(request).map { login =>
        block(request)
      }.getOrElse(Future.successful(onUnauthorised(request)))
  }
}


