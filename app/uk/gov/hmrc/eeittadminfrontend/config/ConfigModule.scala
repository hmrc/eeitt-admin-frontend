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

package uk.gov.hmrc.eeittadminfrontend.config

import com.typesafe.config.{ ConfigFactory, Config => TypeSafeConfig }
import play.api.{ ApplicationLoader, Configuration, Environment, Mode }
import uk.gov.hmrc.play.audit.http.config.AuditingConfig
import uk.gov.hmrc.play.bootstrap.config._

class ConfigModule(val context: ApplicationLoader.Context) {

  val playConfiguration: Configuration = context.initialConfiguration
  val typesafeConfig: TypeSafeConfig = ConfigFactory.load()
  val environment: Environment = context.environment

  val mode: Mode = environment.mode

  val frontendAppConfig = new FrontendAppConfig(playConfiguration)

  val serviceConfig = new ServicesConfig(playConfiguration)

  val controllerConfigs = ControllerConfigs.fromConfig(playConfiguration)

  val auditingConfig: AuditingConfig =
    new AuditingConfigProvider(playConfiguration, frontendAppConfig.appName).get()
}
