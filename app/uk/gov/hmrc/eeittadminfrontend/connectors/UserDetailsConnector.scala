/*
 * Copyright 2019 HM Revenue & Customs
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

import play.api.Play
import uk.gov.hmrc.eeittadminfrontend.WSHttp
import uk.gov.hmrc.eeittadminfrontend.models.UserDetailsData
import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse, InternalServerException }
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.{ ExecutionContext, Future }

object UserDetailsConnector {
  private val sc = new ServicesConfig {
    override protected def mode = Play.current.mode
    override protected val runModeConfiguration = Play.current.configuration
  }

  def userIdbyGroupId(groupId: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[String] =
    WSHttp
      .GET[HttpResponse](s"${sc.baseUrl("user-details")}/user-details/group-identifier/$groupId")
      .map { response =>
        val a = response.json.as[List[UserDetailsData]].filterNot(_.credentialRole != "User")
        a.head.gatewayId
      }
}
