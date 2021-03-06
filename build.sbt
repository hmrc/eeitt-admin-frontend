import sbt.CrossVersion
import uk.gov.hmrc.SbtArtifactory
import uk.gov.hmrc.versioning.SbtGitVersioning.autoImport.majorVersion
import uk.gov.hmrc.DefaultBuildSettings

val appName = "eeitt-admin-frontend"

val silencerVersion = "1.7.3"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(
    play.sbt.PlayScala,
    SbtAutoBuildPlugin,
    SbtGitVersioning,
    SbtDistributablesPlugin,
    SbtArtifactory
  )
  .settings(DefaultBuildSettings.scalaSettings: _*)
  .settings(DefaultBuildSettings.defaultSettings(): _*)
  .settings(
    organization := "uk.gov.hmrc",
    majorVersion := 1,
    scalaVersion := "2.12.13",
    scalafmtOnCompile := true,
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test(),
    libraryDependencies ++= Seq(
      compilerPlugin("com.github.ghik" % "silencer-plugin" % silencerVersion cross CrossVersion.full)
    )
  )
  .settings(
    scalacOptions ++= Seq(
      "-Xfatal-warnings",
      "-Xlint:-missing-interpolator,_",
      "-Yno-adapted-args",
      "-Ywarn-numeric-widen",
      "-Ywarn-value-discard",
      "-Ywarn-dead-code",
      "-deprecation",
      "-feature",
      "-unchecked",
      // silence all warnings on autogenerated files
      "-P:silencer:pathFilters=target/.*",
      // Make sure you only exclude warnings for the project directories, i.e. make builds reproducible
      s"-P:silencer:sourceRoots=${baseDirectory.value.getCanonicalPath}"
    )
  )
  .settings(
    resolvers ++= Seq(
      Resolver.bintrayRepo("jetbrains", "markdown"),
      Resolver.jcenterRepo,
      "bintray-djspiewak-maven" at "https://dl.bintray.com/djspiewak/maven",
      "hmrc-releases" at "https://artefacts.tax.service.gov.uk/artifactory/hmrc-releases/",
      "bintray" at "https://dl.bintray.com/webjars/maven"
    )
  )
  .settings(
    TwirlKeys.templateImports ++= Seq(
      "play.twirl.api.Html",
      "play.twirl.api.HtmlFormat",
      "uk.gov.hmrc.eeittadminfrontend.models._"
    ),
    routesImport ++= Seq(
      "uk.gov.hmrc.eeittadminfrontend.binders.ValueClassBinders._",
      "uk.gov.hmrc.eeittadminfrontend.models.FormTemplateId",
      "uk.gov.hmrc.eeittadminfrontend.models.fileupload.EnvelopeId",
      "uk.gov.hmrc.eeittadminfrontend.deployment._"
    )
  )
