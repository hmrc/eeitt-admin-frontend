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

package uk.gov.hmrc.eeittadminfrontend.connectors

import cats.data.Validated
import play.api.Logger
import play.api.libs.json._
import uk.gov.hmrc.eeittadminfrontend.WSHttp
import uk.gov.hmrc.eeittadminfrontend.models.{Email, LoginError, RegisterError, User}
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpPost}

import scala.concurrent.{ExecutionContext, Future}

class EeittAdminConnector extends ServicesConfig {

  val httpPost : HttpPost = WSHttp

  val eeittAdminUrl : String = "http://localhost:9001/eeitt-admin"

  def checkAuth(email : Email)(implicit hc : HeaderCarrier, ec: ExecutionContext): Future[Validated[LoginError, User]] = {
    httpPost.POST[Email, Validated[LoginError, User]](eeittAdminUrl+"/hello", email)
  }

  def register(user: User)(implicit hc : HeaderCarrier, ec: ExecutionContext) = {
    Logger.debug(user.toString)
    httpPost.POST[User, Validated[RegisterError, String]](eeittAdminUrl+"/register", user)
  }

  def delete(email: Email)(implicit hc : HeaderCarrier, ec: ExecutionContext) = {
    Logger.debug(s"Deleting ")
  }
}

