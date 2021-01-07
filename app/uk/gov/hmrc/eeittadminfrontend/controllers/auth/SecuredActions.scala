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

package uk.gov.hmrc.eeittadminfrontend.controllers.auth

import org.slf4j.{ Logger, LoggerFactory }
import play.api.mvc.{ RequestHeader, Result }
import play.api.Configuration
import uk.gov.hmrc.eeittadminfrontend.auth.AuthConnector
import uk.gov.hmrc.eeittadminfrontend.infrastructure._

import scala.concurrent.Future
import scala.util.Try

trait SecuredActions {

  def whiteListing(r: => Future[Result])(implicit request: RequestHeader): Future[Result]

}

class SecuredActionsImpl(config: Configuration, val authConnector: AuthConnector) extends SecuredActions {

  override def whiteListing(r: => Future[Result])(implicit request: RequestHeader) =
    BasicAuth(WhiteListingConf(config))(r)

}

object WhiteListingConf {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  def apply(config: Configuration): BasicAuthConfiguration = {

    def getWhitelist(config: Configuration): Option[List[Address]] =
      config.getOptional[String]("basicAuth.whitelist").map {
        _.split(",").map(a => Address(a)).toList
      } match {
        case None =>
          logger.warn(
            "Configuration of basicAuth.whitelist has not been provided, so no whitelisting of IP addresses for BasicAuth access")
          None
        case Some(x) =>
          logger.info(
            s""""Whitelisting of IP addresses for BasicAuth access configured to [${x.map(_.ip).mkString(",")}]""")
          Some(x)
      }

    config
      .getOptional[String]("feature.basicAuthEnabled")
      .flatMap(flag => Try(flag.toBoolean).toOption) match {
      case Some(true)  => WhiteListingEnabled(getWhitelist(config))
      case Some(false) => WhiteListingIsDisabled
      case _ => {
        logger.warn(
          "A boolean configuration value has not been provided for feature.basicAuthEnabled, defaulting to false")
        WhiteListingIsDisabled
      }
    }
  }
}

final case class ClientID(id: String)
