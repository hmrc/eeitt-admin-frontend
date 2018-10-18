import sbt._
import play.sbt.PlayImport._
import play.core.PlayVersion

object FrontendBuild extends Build with MicroService {

  val appName = "eeitt-admin-frontend"

  override lazy val appDependencies: Seq[ModuleID] = compile ++ test()

  val compile = Seq(
    ws,
    "uk.gov.hmrc" %% "frontend-bootstrap" % "10.6.0",
    "uk.gov.hmrc" %% "play-partials" % "6.1.0",
    "com.github.pureconfig" %% "pureconfig" % "0.9.2",
    "com.google.gdata" % "core" % "1.47.1",
    "com.google.apis" % "google-api-services-oauth2" % "v2-rev137-1.23.0",
    "org.typelevel" %% "cats-core" % "1.4.0",
    "com.google.api-client" % "google-api-client" % "1.25.0"
  )

  def test(scope: String = "test") = Seq(
    "uk.gov.hmrc" %% "hmrctest" % "3.2.0" % scope,
    "org.scalatest" %% "scalatest" % "3.0.5" % scope,
    "org.pegdown" % "pegdown" % "1.6.0" % scope,
    "org.jsoup" % "jsoup" % "1.11.3" % scope,
    "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
    "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.1" % scope
  )

}
