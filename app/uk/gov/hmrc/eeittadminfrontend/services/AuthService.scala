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

package uk.gov.hmrc.eeittadminfrontend.services

import cats.data._
import cats.syntax.all._
import play.api.{ Configuration, Logger }
import play.api.Configuration
import pureconfig.loadConfigOrThrow
import uk.gov.hmrc.eeittadminfrontend.models.{ Email, LoginError }

case class AuthorisedUsers(users: String)
class AuthService {

  lazy val validUserList: Array[String] = loadConfigOrThrow[AuthorisedUsers]("basicauth").users.split(":")

  def checkUser(email: Email): Validated[LoginError, Unit] = {
    if (validUserList.contains(email.value)) ().valid else LoginError("Unauthorised User").invalid
  }
}
