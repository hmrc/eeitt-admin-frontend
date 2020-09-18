/*
 * Copyright 2020 HM Revenue & Customs
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

import _root_.controllers.AssetsComponents
import com.kenshoo.play.metrics._
import org.slf4j.MDC
import play.api.ApplicationLoader.Context
import play.api._
import play.api.http._
import play.api.i18n._
import play.api.inject.{ Injector, SimpleInjector }
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc._
import play.api.routing.Router
import play.filters.csrf.CSRFComponents
import uk.gov.hmrc.eeittadminfrontend.akka.AkkaModule
import uk.gov.hmrc.eeittadminfrontend.auditing.AuditingModule
import uk.gov.hmrc.eeittadminfrontend.auth.AuthModule
import uk.gov.hmrc.eeittadminfrontend.config.{ ConfigModule, ErrResponder, ErrorHandler }
import uk.gov.hmrc.eeittadminfrontend.connectors.{ FileUploadConnector, GformConnector }
import uk.gov.hmrc.eeittadminfrontend.controllers._
import uk.gov.hmrc.eeittadminfrontend.controllers.auth.SecuredActionsImpl
import uk.gov.hmrc.eeittadminfrontend.metrics.MetricsModule
import uk.gov.hmrc.eeittadminfrontend.playcomponents.{ FrontendFiltersModule, PlayBuiltInsModule }
import uk.gov.hmrc.eeittadminfrontend.services.AuthService
import uk.gov.hmrc.eeittadminfrontend.testonly._
import uk.gov.hmrc.eeittadminfrontend.wshttp.WSHttpModule
import uk.gov.hmrc.play.health.HealthController
import uk.gov.hmrc.govukfrontend.controllers.{ Assets => GovukAssets }
import uk.gov.hmrc.hmrcfrontend.controllers.{ Assets => HmrcAssets }

class ApplicationLoader extends play.api.ApplicationLoader {
  def load(context: Context) = {
    LoggerConfigurator(context.environment.classLoader).foreach {
      _.configure(context.environment)
    }
    val applicationModule = new ApplicationModule(context)
    applicationModule.initialize()
    applicationModule.application
  }
}

class CustomHttpRequestHandler(
  router: Router,
  httpErrorHandler: HttpErrorHandler,
  httpConfiguration: HttpConfiguration,
  httpFilters: Seq[EssentialFilter])
    extends DefaultHttpRequestHandler(router, httpErrorHandler, httpConfiguration, httpFilters: _*) {
  override def routeRequest(request: RequestHeader): Option[Handler] =
    router.handlerFor(request).orElse {
      Some(request.path).filter(_.endsWith("/")).flatMap(p => router.handlerFor(request.copy(path = p.dropRight(1))))
    }
}

class ApplicationModule(context: Context)
    extends BuiltInComponentsFromContext(context) with AssetsComponents with AhcWSComponents with I18nComponents
    with CSRFComponents { self =>

  Logger.info(s"Starting microservice : eeitt-admin-frontend")

  protected val akkaModule = new AkkaModule(materializer, actorSystem)
  protected val configModule = new ConfigModule(context)
  protected val auditingModule = new AuditingModule(configModule)
  private val playBuiltInsModule = new PlayBuiltInsModule(self)

  val errResponder: ErrResponder = new ErrResponder(
    auditingModule.httpAuditingService,
    playBuiltInsModule.i18nSupport
  )(messagesApi, configModule.frontendAppConfig)

  override lazy val httpErrorHandler: ErrorHandler = new ErrorHandler(
    environment,
    configuration,
    configModule.context.sourceMapper,
    errResponder
  )

  private val metricsModule = new MetricsModule(configModule, akkaModule, controllerComponents, executionContext)

  private val sessionCookieBaker: SessionCookieBaker = {
    val httpConfiguration: HttpConfiguration =
      new HttpConfiguration.HttpConfigurationProvider(configModule.playConfiguration, configModule.environment).get

    val config: SessionConfiguration = httpConfiguration.session
    new LegacySessionCookieBaker(config, cookieSigner)
  }

  private val frontendFiltersModule = new FrontendFiltersModule(
    akkaModule,
    configModule,
    auditingModule,
    metricsModule,
    this,
    sessionCookieBaker
  )

  override val httpFilters: Seq[EssentialFilter] = frontendFiltersModule.httpFilters

  override lazy val httpRequestHandler: HttpRequestHandler =
    new CustomHttpRequestHandler(router, httpErrorHandler, httpConfiguration, httpFilters)

  // Don't use uk.gov.hmrc.play.graphite.GraphiteMetricsImpl as it won't allow hot reload due to overridden onStop() method
  lazy val metrics = new MetricsImpl(applicationLifecycle, configuration)

  // We need to create explicit AdminController and provide it into injector so Runtime DI could be able
  // to find it when endpoints in health.Routes are being called
  val messagesControllerComponents: MessagesControllerComponents = new DefaultMessagesControllerComponents(
    new DefaultMessagesActionBuilderImpl(defaultBodyParser, playBuiltInsModule.messagesApi),
    defaultActionBuilder,
    playBodyParsers,
    playBuiltInsModule.messagesApi,
    playBuiltInsModule.langs,
    fileMimeTypes,
    executionContext
  )

  lazy val healthController = new HealthController(configuration, environment, messagesControllerComponents)

  val customInjector: Injector =
    new SimpleInjector(injector) + wsClient

  val wSHttpModule = new WSHttpModule(auditingModule, configModule, akkaModule, this)
  val authModule = new AuthModule(configModule, wSHttpModule)

  val authService = new AuthService()
  val securedActions = new SecuredActionsImpl(configuration, authModule.authConnector)
  val authController =
    new AuthController(authModule.authConnector, securedActions, authService, messagesControllerComponents)(
      configModule.frontendAppConfig)

  val fileUploadConnector = new FileUploadConnector(wSHttpModule.auditableWSHttp, configModule.serviceConfig)
  val gformConnector = new GformConnector(wSHttpModule.auditableWSHttp, configModule.serviceConfig)

  val govukfrontendAssets = new GovukAssets(httpErrorHandler, assetsMetadata)
  val hmrcfrontendAssets = new HmrcAssets(httpErrorHandler, assetsMetadata)

  val govukRoutes: govuk.Routes = new govuk.Routes(httpErrorHandler, govukfrontendAssets)
  val hmrcfrontendRoutes: hmrcfrontend.Routes = new hmrcfrontend.Routes(httpErrorHandler, hmrcfrontendAssets)

  val gformController = new GformsController(authModule.authConnector, gformConnector, messagesControllerComponents)(
    executionContext,
    configModule.frontendAppConfig,
    materializer)
  val fileUploadController =
    new FileUploadController(authModule.authConnector, fileUploadConnector, messagesControllerComponents)(
      executionContext,
      configModule.frontendAppConfig)
  val submissionController = new SubmissionController(
    authModule.authConnector,
    gformConnector,
    fileUploadConnector,
    messagesControllerComponents)(executionContext, configModule.frontendAppConfig)

  val appRoutes = new app.Routes(
    httpErrorHandler,
    govukRoutes,
    hmrcfrontendRoutes,
    authController,
    gformController,
    fileUploadController,
    submissionController,
    this.assets
  )

  val prodRoutes = new prod.Routes(
    httpErrorHandler,
    appRoutes,
    new HealthController(configModule.playConfiguration, configModule.environment, messagesControllerComponents),
    metricsModule.metricsController
  )

  val testOnlyController = new TestOnlyController(messagesControllerComponents)

  val testRoutes = new testOnlyDoNotUseInAppConf.Routes(httpErrorHandler, prodRoutes, testOnlyController)

  val testOnly = configuration.getOptional[String]("application.router").contains("testOnlyDoNotUseInAppConf.Routes")

  override lazy val router: Router = if (testOnly) testRoutes else prodRoutes

  override lazy val application: Application = new DefaultApplication(
    environment,
    applicationLifecycle,
    customInjector,
    configuration,
    requestFactory,
    httpRequestHandler,
    httpErrorHandler,
    actorSystem,
    materializer)

  def initialize() = {
    val appName = configModule.frontendAppConfig.appName
    Logger.info(s"Starting $appName in mode ${environment.mode}")
    MDC.put("appName", appName)
    val loggerDateFormat: Option[String] = configuration.getOptional[String]("logger.json.dateformat")
    loggerDateFormat.foreach(str => MDC.put("logger.json.dateformat", str))
    Logger.info(
      s"Started $appName in mode ${environment.mode} at port ${application.configuration.getOptional[String]("http.port")}")
  }
}
