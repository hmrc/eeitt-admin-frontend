import sbt._
import play.sbt.PlayImport._
import play.core.PlayVersion

object FrontendBuild extends Build with MicroService {

  val appName = "eeitt-admin-frontend"

  override lazy val appDependencies: Seq[ModuleID] = compile ++ test()

  val compile = Seq(
    ws,
    "uk.gov.hmrc" %% "frontend-bootstrap" % "8.11.0",
    "uk.gov.hmrc" %% "play-partials" % "6.1.0",
    "com.github.pureconfig" %% "pureconfig" % "0.7.2",
    "com.google.gdata" % "core" % "1.47.1",
    "com.google.apis" % "google-api-services-oauth2" % "v2-rev124-1.22.0",
    "org.typelevel" % "cats-core_2.11" % "0.9.0",
    "com.google.api-client" % "google-api-client" % "1.22.0"
  )

  def test(scope: String = "test") = Seq(
    "uk.gov.hmrc" %% "hmrctest" % "3.0.0" % scope,
    "org.scalatest" %% "scalatest" % "3.0.1" % scope,
    "org.pegdown" % "pegdown" % "1.6.0" % scope,
    "org.jsoup" % "jsoup" % "1.10.2" % scope,
    "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
    "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.0" % scope
  )

}
