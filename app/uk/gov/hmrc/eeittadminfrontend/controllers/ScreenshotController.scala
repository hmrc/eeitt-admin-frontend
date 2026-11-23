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
import java.nio.file.{ Files, Paths }
import scala.util.Try
import scala.jdk.CollectionConverters._
import javax.inject.Inject

class ScreenshotController @Inject() (
  frontendAuthComponents: FrontendAuthComponents,
  messagesControllerComponents: MessagesControllerComponents,
  proxyProvider: ProxyProvider,
  configuration: Configuration
) extends GformAdminFrontendController(frontendAuthComponents, messagesControllerComponents) with I18nSupport {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  private def logDiagnostics(): Unit = {
    val tmpDir = Option(System.getProperty("playwright.driver.tmpdir")).getOrElse(System.getProperty("java.io.tmpdir"))
    val tmpPath = Paths.get(tmpDir)
    val osRelease =
      Try(Files.readAllLines(Paths.get("/etc/os-release")).asScala.take(3).mkString("; ")).getOrElse("n/a")
    val cgroupMem =
      Try(Files.readAllLines(Paths.get("/sys/fs/cgroup/memory.max")).asScala.mkString).getOrElse("n/a")
    val rt = Runtime.getRuntime
    logger.info(
      s"Playwright diagnostics: os=${System.getProperty("os.name")} arch=${System.getProperty("os.arch")} " +
        s"osRelease=[$osRelease] user=${System.getProperty("user.name")} home=${System.getProperty("user.home")} " +
        s"cwd=${new File(".").getAbsolutePath} tmpDir=$tmpDir exists=${Files.exists(tmpPath)} " +
        s"writable=${Files.isWritable(tmpPath)} usableSpaceMB=${Try(tmpPath.toFile.getUsableSpace / 1048576).getOrElse(-1L)} " +
        s"cgroupMemoryMax=$cgroupMem jvmMaxMB=${rt.maxMemory / 1048576} jvmFreeMB=${rt.freeMemory / 1048576} " +
        s"driverTmpdirProp=${System.getProperty("playwright.driver.tmpdir")}"
    )
    Try(Files.list(tmpPath).iterator().asScala.filter(_.getFileName.toString.startsWith("playwright-java")).toList)
      .foreach(_.foreach { d =>
        val node = d.resolve("node")
        logger.info(
          s"Playwright driver dir $d: node exists=${Files.exists(node)} size=${Try(Files.size(node)).getOrElse(-1L)} " +
            s"executable=${Files.isExecutable(node)}"
        )
      })
    val _ = Try {
      val pb = new ProcessBuilder("sh", "-c", "mount | grep -E ' /tmp | / ' ; uname -a").redirectErrorStream(true)
      val out = scala.io.Source.fromInputStream(pb.start().getInputStream).mkString
      logger.info(s"Playwright mount/uname: $out")
    }
  }

  private def runAndLog(label: String, cmd: String): Unit = {
    val _ = Try {
      val pb = new ProcessBuilder("sh", "-c", cmd).redirectErrorStream(true)
      val proc = pb.start()
      val out = scala.io.Source.fromInputStream(proc.getInputStream).mkString
      logger.error(s"Playwright $label (exit=${proc.waitFor()}): $out")
    }.recover { case e => logger.error(s"Playwright $label failed to run", e) }
  }

  // The driver's stderr is not captured by the platform logs, so run the binaries ourselves and log their output.
  private def logDriverFailure(browsersPath: Option[String]): Unit = {
    runAndLog(
      "node --version",
      "for n in ${TMPDIR:-/tmp}/playwright-java-*/node; do echo $n; $n --version; ldd $n | grep 'not found'; done"
    )
    browsersPath.foreach { path =>
      runAndLog(
        "chrome-headless-shell ldd/version",
        s"for c in $path/chromium_headless_shell-*/chrome-headless-shell-linux64/chrome-headless-shell; do echo $$c; ldd $$c | grep 'not found'; $$c --version; done"
      )
    }
  }

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
    logDiagnostics()
    val options = new Playwright.CreateOptions()

    //Enables pre-downloaded browser for any HMRC environment.
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

    val playwright: Playwright =
      try Playwright.create(options)
      catch {
        case e: Throwable =>
          logger.error("Playwright.create failed", e)
          logDriverFailure(Option(options.env).flatMap(_.asScala.get("PLAYWRIGHT_BROWSERS_PATH")))
          throw e
      }
    val browser: Browser = browserType match {
      case "chrome"  => playwright.chromium().launch(launchOptions)
      case "firefox" => playwright.firefox().launch(launchOptions)
      case "webkit"  => playwright.webkit().launch(launchOptions)
      case _         => throw new RuntimeException(s"$browserType no such browser")
    }
    val context: BrowserContext = browser.newContext()
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
