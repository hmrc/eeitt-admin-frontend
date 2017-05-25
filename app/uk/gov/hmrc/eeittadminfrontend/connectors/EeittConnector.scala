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

import play.api.Logger
import play.api.mvc.Request
import uk.gov.hmrc.eeittadminfrontend.WSHttp
import uk.gov.hmrc.eeittadminfrontend.models._
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet, HttpPost}

import scala.concurrent.{ExecutionContext, Future}

trait EeittConnector[A] extends ServicesConfig {

  val httpGet : HttpGet = WSHttp
  val httpPost : HttpPost = WSHttp

  val eeittAdminUrl : String = "http://localhost:9191/eeitt"

  def apply(a : A)(implicit hc : HeaderCarrier, ec: ExecutionContext, request : Request[Map[String, Seq[String]]]) : Future[List[Response]]
}

object EeittConnector {

  private def getEeittConnector[A](getPath : A => String): EeittConnector[A] = {
    new EeittConnector[A] {

      override def apply(value: A)(implicit hc: HeaderCarrier, ec: ExecutionContext, request : Request[Map[String, Seq[String]]]): Future[List[Response]] = {
        def call[B](a : String, b : B): Future[List[Response]] = {
          b match {
            case ETMP =>
              httpGet.GET[Either[List[ETMPResponseBusiness], List[ETMPResponseAgent]]](eeittAdminUrl + getPath(value) + a).map{
                case Left(x) => x
                case Right(y) => y
              }
            case x =>
              httpGet.GET[List[EnrollmentResponse]](eeittAdminUrl + getPath(value) + a)
          }
        }

        value match {
          case RegistrationNumber(x, y) =>
            Logger.info(s" ${request.session.get("token").get} Queried for RegNumber in $y Database")
            call(x, y)
          case Arn(x, y) =>
            Logger.info(s" ${request.session.get("token").get} Queried for Arn in $y Database")
            call(x, y)
          case GroupId(x, y) =>
            Logger.info(s" ${request.session.get("token").get} Queried for GroupId in $y Database")
            call(x, y)
          case Regime(x, y) =>
            Logger.info(s" ${request.session.get("token").get} Queried for RegNumber in $y Database")
            call(x, y)
          case _ =>
            Logger.error("No Database nor User detected")
            Future.successful(List(FailureResponse("No Database nor User detected")))
        }
      }
    }
  }

  implicit def arnConnector : EeittConnector[Arn] = {
    Logger.info("ARN")
    getEeittConnector[Arn](_.database.agent.get)
  }

  implicit def regConnector: EeittConnector[RegistrationNumber] = {
    Logger.info("REG SEARCH")
    getEeittConnector[RegistrationNumber](_.database.reg.get)
  }

  implicit def groupIdConnector: EeittConnector[GroupId] = {
    Logger.info("GROUP")
    getEeittConnector[GroupId](_.userType.url)
  }

  implicit def regimeConnector: EeittConnector[Regime] = {
    Logger.info("REGIME")
    getEeittConnector[Regime](_.database.regime.get) // Business Only
  }
}


