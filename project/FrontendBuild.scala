import sbt._
import play.sbt.PlayImport._
import play.core.PlayVersion

object FrontendBuild extends Build with MicroService {

  val appName = "eeitt-admin-frontend"

  override lazy val appDependencies: Seq[ModuleID] = compile ++ test()

  val compile = Seq(
    ws,
    "uk.gov.hmrc" %% "frontend-bootstrap" % "12.4.0",
    "uk.gov.hmrc" %% "play-partials" % "6.5.0",
    "com.github.pureconfig" %% "pureconfig" % "0.10.2",
    "com.google.gdata" % "core" % "1.47.1",
    "com.google.apis" % "google-api-services-oauth2" % "v2-rev141-1.25.0",
    "org.typelevel" %% "cats-core" % "1.6.0",
    "com.google.api-client" % "google-api-client" % "1.28.0",
    "org.julienrf" %% "play-json-derived-codecs" % "3.3"
  )

  def test(scope: String = "test") = Seq(
    "uk.gov.hmrc" %% "hmrctest" % "3.5.0-play-25" % scope,
    "org.scalatest" %% "scalatest" % "3.0.5" % scope,
    "org.pegdown" % "pegdown" % "1.6.0" % scope,
    "org.jsoup" % "jsoup" % "1.11.3" % scope,
    "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
    "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.1" % scope,
    "com.github.tomakehurst" % "wiremock" % "2.19.0" % scope
  )
}
