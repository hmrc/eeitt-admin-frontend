import com.lucidchart.sbt.scalafmt.ScalafmtCorePlugin.autoImport.scalafmtOnCompile
import sbt.Keys._
import sbt._
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._
import play.sbt.routes.RoutesKeys.routesImport
import play.twirl.sbt.Import.TwirlKeys

trait MicroService {

  import uk.gov.hmrc._
  import DefaultBuildSettings._
  import uk.gov.hmrc.SbtAutoBuildPlugin
  import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
  import uk.gov.hmrc.versioning.SbtGitVersioning
  import uk.gov.hmrc.versioning.SbtGitVersioning.autoImport.majorVersion

  val appName: String

  lazy val appDependencies: Seq[ModuleID] = ???
  lazy val plugins: Seq[Plugins] = Seq.empty
  lazy val playSettings: Seq[Setting[_]] = Seq.empty

  lazy val microservice = Project(appName, file("."))
    .enablePlugins(Seq(
      play.sbt.PlayScala,
      SbtAutoBuildPlugin,
      SbtGitVersioning,
      SbtDistributablesPlugin,
      SbtArtifactory) ++ plugins: _*)
    .settings(majorVersion := 1)
    .settings(playSettings: _*)
    .settings(scalaSettings: _*)
    .settings(publishingSettings: _*)
    .settings(defaultSettings(): _*)
    .settings(
      scalafmtOnCompile := true,
      scalaVersion := "2.11.12",
      libraryDependencies ++= appDependencies,
      routesImport ++= Seq(
        "uk.gov.hmrc.eeittadminfrontend.binders.ValueClassBinders._",
        "uk.gov.hmrc.eeittadminfrontend.models.FormTemplateId",
        "uk.gov.hmrc.eeittadminfrontend.models.fileupload.EnvelopeId"
      )
    )
    .settings(resolvers ++= Seq(
      Resolver.bintrayRepo("jetbrains", "markdown"),
      Resolver.jcenterRepo,
      "bintray-djspiewak-maven" at "https://dl.bintray.com/djspiewak/maven",
      "hmrc-releases" at "https://artefacts.tax.service.gov.uk/artifactory/hmrc-releases/",
      "bintray" at "https://dl.bintray.com/webjars/maven"
    ))
    .settings(TwirlKeys.templateImports ++= Seq(
      "play.twirl.api.Html",
      "play.twirl.api.HtmlFormat",
      "uk.gov.hmrc.eeittadminfrontend.models._",
      "uk.gov.hmrc.govukfrontend.views.html.components._",
      "uk.gov.hmrc.govukfrontend.views.html.helpers._",
      "uk.gov.hmrc.hmrcfrontend.views.html.components._"
    ))
}
