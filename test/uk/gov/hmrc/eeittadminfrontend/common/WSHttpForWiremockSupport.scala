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

package uk.gov.hmrc.eeittadminfrontend.common

import akka.actor.ActorSystem
import com.typesafe.config.{ Config, ConfigFactory }
import play.api.libs.ws.WSClient
import play.api.test.WsTestClient.InternalWSClient
import uk.gov.hmrc.eeittadminfrontend.wshttp.WSHttp
import uk.gov.hmrc.http.hooks.HttpHook

import scala.collection.JavaConverters._

trait WSHttpForWiremockSupport {
  wireMockSupport: WiremockSupport =>
  val _actorSystem: ActorSystem = ActorSystem("WSHttpSupport")
  val _wsClient: WSClient = new InternalWSClient("http", wiremockPort)
  val wsHttp: WSHttp = new WSHttp {
    override val actorSystem: ActorSystem = _actorSystem
    override val configuration: Config = ConfigFactory.parseMap(
      Map(
        "internalServiceHostPatterns"     -> List().asJava,
        "bootstrap.http.headersAllowlist" -> List().asJava,
        "http-verbs.retries.intervals"    -> List().asJava
      ).asJava
    )
    override val hooks: Seq[HttpHook] = Seq.empty
    override val wsClient: WSClient = _wsClient
  }

  def closeClient(): Unit = {
    _actorSystem.terminate()
    _wsClient.close()
  }
}
