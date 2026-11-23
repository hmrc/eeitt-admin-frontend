/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.eeittadminfrontend.controllers

import com.microsoft.playwright._
import org.slf4j.{ Logger, LoggerFactory }
import play.api.Configuration
import play.api.i18n.I18nSupport
import play.api.mvc.MessagesControllerComponents
import uk.gov.hmrc.eeittadminfrontend.proxy.ProxyProvider
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents

import java.io.File
import scala.jdk.CollectionConverters._
import javax.inject.Inject

class ScreenshotController @Inject() (
  frontendAuthComponents: FrontendAuthComponents,
  messagesControllerComponents: MessagesControllerComponents,
  proxyProvider: ProxyProvider,
  configuration: Configuration
) extends GformAdminFrontendController(frontendAuthComponents, messagesControllerComponents) with I18nSupport {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  // Chromium ignores the JVM proxy settings, so the platform proxy is passed to the browser explicitly.
  private def launchOptions: BrowserType.LaunchOptions = {
    val launchOptions = new BrowserType.LaunchOptions()
    proxyProvider.maybeProxy.foreach { proxy =>
      proxy.address() match {
        case address: java.net.InetSocketAddress =>
          val server = s"http://${address.getHostString}:${address.getPort}"
          val playwrightProxy = new com.microsoft.playwright.options.Proxy(server)
          configuration.getOptional[String]("proxy.username").foreach(playwrightProxy.setUsername)
          configuration.getOptional[String]("proxy.password").foreach(playwrightProxy.setPassword)
          logger.info(s"Playwright browser will use proxy $server")
          launchOptions.setProxy(playwrightProxy)
        case _ =>
      }
    }
    launchOptions
  }

  def test(browserType: String) = authorizedRead { request =>
    val options = new Playwright.CreateOptions()

    new File(".").listFiles
      .find { file =>
        file.isDirectory && file.getName.startsWith("eeitt-admin-frontend-")
      }
      .foreach { file =>
        val path = file.getPath + "/conf/browsers/bin"
        options.setEnv(
          Map(
            "PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD" -> "1",
            "PLAYWRIGHT_BROWSERS_PATH"         -> path,
            "DEBUG"                            -> "pw:*"
          ).asJava
        )
      }

    val optionsAsScalaMap: scala.collection.mutable.Map[String, String] = options.env.asScala

    logger.info("Playwright options: " + optionsAsScalaMap)

    val playwright: Playwright = Playwright.create(options)

    val browser: Browser = browserType match {
      case "chrome" => playwright.chromium().launch(launchOptions)
      case _        => throw new RuntimeException(s"$browserType no such browser")
    }
    val context: BrowserContext = browser.newContext(
      new Browser.NewContextOptions()
        .setViewportSize(1280, 1024)
        .setDeviceScaleFactor(2.0) // 2x pixel density gives sharper text
    )
    val page: Page = context.newPage()
    request.getQueryString("url") match {
      case Some(url) =>
        logger.info(s"Playwright navigating to $url")
        page.navigate(url)
      // Inline HTML needs no network access, so it only verifies that the browser itself works.
      case None => page.setContent("<html><body><h1>Playwright works</h1></body></html>")
    }

    val screenshot = page.screenshot(
      new Page.ScreenshotOptions().setFullPage(true)
    )
    playwright.close()
    Ok(screenshot).as("image/png")
  }
}
