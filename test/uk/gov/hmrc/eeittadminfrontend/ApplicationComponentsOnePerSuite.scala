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

package uk.gov.hmrc.eeittadminfrontend

import java.io.File

import org.scalatest.TestSuite
import org.scalatestplus.play.{ FakeApplicationFactory, OneAppPerSuite }
import play.api.i18n.{ DefaultLangs, DefaultMessagesApi }
import play.api.{ Configuration, Environment, Mode }
import uk.gov.hmrc.eeittadminfrontend.support.WireMockSupport
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector

trait ApplicationComponentsOnePerSuite extends OneAppPerSuite with FakeApplicationFactory with WireMockSupport {
  this: TestSuite =>

  override def commonStubs(): Unit = ()

  def additionalConfiguration: Map[String, Any] = Map.empty[String, Any]

  private lazy val config = Configuration.from(additionalConfiguration)

  protected implicit val materializer = app.materializer

  override lazy val fakeApplication =
    new ApplicationLoader().load(context.copy(initialConfiguration = context.initialConfiguration ++ config))

  def context: play.api.ApplicationLoader.Context = {
    val classLoader = play.api.ApplicationLoader.getClass.getClassLoader
    val env = new Environment(new File("."), classLoader, Mode.Test)
    play.api.ApplicationLoader.createContext(env)
  }

  val configuration: Configuration = Configuration.reference
  val mode: Mode.Mode = Mode.Test
  val env: Environment = Environment.simple(mode = mode)
  val langs = new DefaultLangs(configuration)

  implicit val messageApi = new DefaultMessagesApi(env, configuration, langs)
  implicit val appConfig = new AppConfig {
    val analyticsToken: String = ""
    val analyticsHost: String = ""
    val reportAProblemPartialUrl: String = ""
    val reportAProblemNonJSUrl: String = ""
  }

  class FakeAuthConnector extends AuthConnector {
    override val serviceUrl: String = ""
    override val http = WSHttp
  }
}
