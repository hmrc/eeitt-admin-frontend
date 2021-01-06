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

package uk.gov.hmrc.eeittadminfrontend

import java.io.File
import org.scalatest.TestSuite
import org.scalatestplus.play.{ BaseOneAppPerSuite, FakeApplicationFactory }
import play.api.ApplicationLoader.Context
import play.api.i18n.{ DefaultLangs, DefaultMessagesApi }
import play.api.{ Configuration, Environment, Mode }
import uk.gov.hmrc.eeittadminfrontend.auth.AuthConnector
import uk.gov.hmrc.eeittadminfrontend.config.AppConfig
import uk.gov.hmrc.eeittadminfrontend.support.WireMockSupport

trait ApplicationComponentsOnePerSuite extends BaseOneAppPerSuite with FakeApplicationFactory with WireMockSupport {
  this: TestSuite =>

  override def commonStubs(): Unit = ()

  def additionalConfiguration: Map[String, Any] = Map.empty[String, Any]

  private lazy val additionalConfig = Configuration.from(additionalConfiguration)

  protected implicit val materializer = app.materializer

  protected lazy val initialConfig = context.initialConfiguration ++ additionalConfig

  override def fakeApplication = new ApplicationLoader().load(context.copy(initialConfiguration = initialConfig))

  def context: play.api.ApplicationLoader.Context = {
    val classLoader = play.api.ApplicationLoader.getClass.getClassLoader
    val env = new Environment(new File("."), classLoader, Mode.Test)
    Context.create(env)
  }

  val configuration: Configuration = Configuration.reference
  val mode: Mode = Mode.Test
  val env: Environment = Environment.simple(mode = mode)
  val langs = new DefaultLangs()

  implicit val messageApi = new DefaultMessagesApi()
  implicit val appConfig = new AppConfig {
    val appName: String = ""
    val analyticsToken: String = ""
    val analyticsHost: String = ""
    val reportAProblemPartialUrl: String = ""
    val reportAProblemNonJSUrl: String = ""
    val optimizelyUrl: Option[String] = None
    val footerCookiesUrl: String = ""
    val footerPrivacyPolicyUrl: String = ""
    val footerTermsConditionsUrl: String = ""
    val footerHelpUrl: String = ""
  }

  class FakeAuthConnector extends AuthConnector("", null, configuration)
}
