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
import org.scalatestplus.play.{ BaseOneAppPerSuite, FakeApplicationFactory }
import play.api.{ Configuration, Environment, Mode }

trait ApplicationComponentsOnePerSuite extends BaseOneAppPerSuite with FakeApplicationFactory{
  this: TestSuite =>

  def additionalConfiguration: Map[String, Any] = Map.empty[String, Any]

  private lazy val config = Configuration.from(additionalConfiguration)

  override lazy val fakeApplication =
    new ApplicationLoader().load(context.copy(initialConfiguration = context.initialConfiguration ++ config))

  def context: play.api.ApplicationLoader.Context = {
    val classLoader = play.api.ApplicationLoader.getClass.getClassLoader
    val env = new Environment(new File("."), classLoader, Mode.Test)
    play.api.ApplicationLoader.createContext(env)
  }
}
