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

package uk.gov.hmrc.eeittadminfrontend.infrastructure

import org.slf4j.{ Logger, LoggerFactory }
import play.api.mvc.Results.Status
import play.api.mvc.{ RequestHeader, Result }

import scala.concurrent.Future

trait BasicAuth {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  def whitelistPassed(address: Option[Address]): Boolean

  def apply(block: => Future[Result])(implicit request: RequestHeader) = {
    val trueClient = "True-Client-IP"
    val maybeSource = request.headers.get(trueClient).map(Address)
    val forwardedFor = request.headers.get("x-forwarded-for").getOrElse("none")
    logger.info(s"""Remote address ${request.remoteAddress}, x-forwarded-for $forwardedFor, True-Client-IP ${maybeSource
      .getOrElse("none")}""")
    if (whitelistPassed(maybeSource))
      block
    else
      Future.successful(new Status(403))
  }

}

object AlwaysAuthorisedBasicAuth extends BasicAuth {

  override def whitelistPassed(address: Option[Address]): Boolean = true

}

class AuthorisingBasicAuth(whitelist: Option[List[Address]]) extends BasicAuth {

  def whitelistPassed(address: Option[Address]): Boolean =
    whitelist match {
      case Some(w) =>
        address match {
          case Some(a) =>
            w.contains(a)
          case None =>
            false
        }
      case None =>
        true
    }

}

object BasicAuth {

  def apply(config: BasicAuthConfiguration): BasicAuth =
    config match {
      case WhiteListingIsDisabled       => AlwaysAuthorisedBasicAuth
      case WhiteListingEnabled(address) => new AuthorisingBasicAuth(address)
    }

}

sealed abstract class BasicAuthConfiguration

case object WhiteListingIsDisabled extends BasicAuthConfiguration

case class WhiteListingEnabled(whitelist: Option[List[Address]]) extends BasicAuthConfiguration

case class User(name: String, password: String)

case class Address(ip: String)
