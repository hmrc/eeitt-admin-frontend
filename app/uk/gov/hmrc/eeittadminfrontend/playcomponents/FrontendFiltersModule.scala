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

package uk.gov.hmrc.eeittadminfrontend.playcomponents

import akka.stream.Materializer
import play.api.mvc.{ EssentialFilter, SessionCookieBaker }
import play.filters.csrf.CSRFComponents
import play.filters.headers.SecurityHeadersFilter
import uk.gov.hmrc.crypto.ApplicationCrypto
import uk.gov.hmrc.eeittadminfrontend.akka.AkkaModule
import uk.gov.hmrc.eeittadminfrontend.auditing.AuditingModule
import uk.gov.hmrc.eeittadminfrontend.config.ConfigModule
import uk.gov.hmrc.eeittadminfrontend.metrics.MetricsModule
import uk.gov.hmrc.play.bootstrap.config.DefaultHttpAuditEvent
import uk.gov.hmrc.play.bootstrap.filters.{ CacheControlConfig, CacheControlFilter, DefaultLoggingFilter, MDCFilter }
import uk.gov.hmrc.play.bootstrap.frontend.filters.crypto.{ DefaultSessionCookieCryptoFilter, SessionCookieCrypto, SessionCookieCryptoFilter, SessionCookieCryptoProvider }
import uk.gov.hmrc.play.bootstrap.frontend.filters.deviceid.DefaultDeviceIdFilter
import uk.gov.hmrc.play.bootstrap.frontend.filters.{ DefaultFrontendAuditFilter, HeadersFilter, SessionIdFilter, SessionTimeoutFilter, SessionTimeoutFilterConfig }

import scala.concurrent.ExecutionContext

class FrontendFiltersModule(
  akkaModule: AkkaModule,
  configModule: ConfigModule,
  auditingModule: AuditingModule,
  metricsModule: MetricsModule,
  csrfComponents: CSRFComponents,
  sessionCookieBaker: SessionCookieBaker
)(implicit ec: ExecutionContext) { self =>
  private implicit val materializer: Materializer = akkaModule.materializer

  private val frontendAuditFilter = new DefaultFrontendAuditFilter(
    configModule.playConfiguration,
    configModule.controllerConfigs,
    auditingModule.auditConnector,
    new DefaultHttpAuditEvent(configModule.frontendAppConfig.appName),
    materializer
  ) {
    override val maskedFormFields = Seq("password")
  }

  private val sessionCookieCryptoFilter: SessionCookieCryptoFilter = {
    val applicationCrypto: ApplicationCrypto = new ApplicationCrypto(configModule.typesafeConfig)
    val sessionCookieCrypto: SessionCookieCrypto = new SessionCookieCryptoProvider(applicationCrypto).get()

    new DefaultSessionCookieCryptoFilter(sessionCookieCrypto, sessionCookieBaker)
  }

  private val cacheControlFilter: CacheControlFilter = {
    val cacheControlConfig: CacheControlConfig = CacheControlConfig.fromConfig(configModule.playConfiguration)
    new CacheControlFilter(cacheControlConfig, materializer)
  }

  private val mdcFilter: MDCFilter =
    new MDCFilter(materializer, configModule.playConfiguration, configModule.frontendAppConfig.appName)

  private val sessionTimeoutFilter = new SessionTimeoutFilter(
    SessionTimeoutFilterConfig.fromConfig(configModule.playConfiguration)
  )

  private val deviceIdFilter = new DefaultDeviceIdFilter(
    configModule.frontendAppConfig.appName,
    configModule.playConfiguration,
    auditingModule.auditConnector
  )

  private val securityHeadersFilter = SecurityHeadersFilter(configModule.playConfiguration)

  private val headersFilter = new HeadersFilter(materializer)

  private val loggingFilter = new DefaultLoggingFilter(configModule.controllerConfigs)

  private val sessionIdFilter = new SessionIdFilter(materializer, ec, sessionCookieBaker)

  lazy val httpFilters: Seq[EssentialFilter] = List(
    securityHeadersFilter,
    metricsModule.metricsFilter,
    sessionCookieCryptoFilter,
    headersFilter,
    deviceIdFilter,
    loggingFilter,
    frontendAuditFilter,
    sessionTimeoutFilter,
    csrfComponents.csrfFilter,
    cacheControlFilter,
    mdcFilter,
    sessionIdFilter
  )
}
