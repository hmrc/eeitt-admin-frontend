import com.typesafe.sbt.packager.Keys.dist
import sbt.*
import sbt.Keys.*

import scala.sys.process.Process

/** Settings defined in this file are run as part of the dist stage.
  */
object PlaywrightBrowserDownload {
  val playwrightBrowserDownload = TaskKey[Int]("playwright-browser-download")

  private val browserDir = System.getProperty("os.name").toLowerCase match {
    case mac if mac.contains("mac")  => sys.props("user.home") + "/Library/Caches/ms-playwright/chromium_headless_shell-1200"
    case win if win.contains("win") => throw new RuntimeException("Windows not available") //sys.props("user.home") + "\\AppData\\Local\\ms-playwright"
    case linux if linux.contains("linux") => sys.props("user.home") + "/.cache/ms-playwright/."
    case osName => throw new RuntimeException(s"Unknown operating system $osName")
  }

  val playwrightBrowserDownloadSetting: Seq[sbt.Def.Setting[_]] = Seq(
    playwrightBrowserDownload := {
      val browsersDir = (baseDirectory.value / "conf" / "browsers")
      val status = Process("npm install playwright --no-save") #&&
        Process("npm exec playwright install --with-deps chromium") #&&
        Process("mkdir " + browsersDir.getAbsolutePath) ###
        Process(Seq("cp","-R",browserDir ,browsersDir.getAbsolutePath )) #&&
        Process(Seq(
          "sh",
          "-c",
          "find . -type d -exec ls -lah {} \\;"
        ), browsersDir) !

      val dirs = (browsersDir ** DirectoryFilter).get

      dirs.foreach { dir =>
        Process(Seq("ls", "-lah"), dir).!
      }

      status
    },
    dist := { dist dependsOn playwrightBrowserDownload }.value
  )
}
