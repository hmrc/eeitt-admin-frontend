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
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.eeittadminfrontend.WSHttp
import uk.gov.hmrc.eeittadminfrontend.models._
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet, HttpPost}

import scala.concurrent.{ExecutionContext, Future}

trait EeittConnector[A] extends ServicesConfig {

  val httpGet : HttpGet = WSHttp

  val eeittAdminUrl : String

  def apply(a : A)(implicit hc : HeaderCarrier, ec: ExecutionContext) : Future[JsValue]
}

object EeittConnector {

  private def thingy[A, B](fromAtoString: A => String, fromAtoB: A => B, path: String): EeittConnector[A] = {
    new EeittConnector[A] {

      override val eeittAdminUrl: String = "http://localhost:9191/eeitt"

      override def apply(value: A)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[JsValue] = {
        fromAtoB(value) match {
          case ETMP =>
            Logger.error("ETMP HIT")
            println(eeittAdminUrl+path+fromAtoString(value))
            httpGet.GET[JsValue](eeittAdminUrl + path + fromAtoString(value))
          case Enrollments =>
            Logger.error("ENROLLMENT HIT")
            println(eeittAdminUrl+path+fromAtoString(value))
            httpGet.GET[JsValue](eeittAdminUrl + path + fromAtoString(value))
          case Agent =>
            Logger.error("AGENT HIT")
            println(eeittAdminUrl+path+fromAtoString(value))
            httpGet.GET[JsValue](eeittAdminUrl + path + "-business-users/" + fromAtoString(value))
          case Business =>
            Logger.error("Business User HIT")
            println(eeittAdminUrl+path+fromAtoString(value))
            httpGet.GET[JsValue](eeittAdminUrl + path + "agents/" + fromAtoString(value))
          case _ =>
            Logger.error("typeclasses not met")
            Future.successful(Json.obj("bob" -> "hello"))
        }
      }
    }
  }

  implicit def regArn : EeittConnector[Arn] = {
    Logger.info("ARN")
    thingy[Arn, Database](_.arn, _.database, "/get-agents/")
  }

  implicit def reg: EeittConnector[RegistrationNumber] = {
    Logger.info("REG SEARCH")
    thingy[RegistrationNumber, Database](_.registration, _.database, "/get-business-users/")
  }

  implicit def group: EeittConnector[GroupId] = {
    Logger.info("GROUP")
    thingy[GroupId, UserType](_.groupid, _.userType, s"/get-")
  }

  implicit def regime: EeittConnector[Regime] = {
    Logger.info("REGIME")
    thingy[Regime, Database](_.regime, _.database, "/business-user-by-regime/") // Business Only
  }
}


