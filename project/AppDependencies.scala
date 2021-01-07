import sbt._
import play.sbt.PlayImport._
import play.core.PlayVersion

object AppDependencies {

  val compile = Seq(
    ws,
    "uk.gov.hmrc" %% "bootstrap-frontend-play-27" % "3.2.0",
    "uk.gov.hmrc"                  %% "play-partials"            % "7.1.0-play-27",
    "uk.gov.hmrc"                  %% "play-frontend-govuk"      % "0.56.0-play-27",
    "uk.gov.hmrc"                  %% "play-frontend-hmrc"       % "0.34.0-play-27",
    "com.github.pureconfig" %% "pureconfig"               % "0.14.0",
    "org.typelevel"                %% "cats-core"                % "2.2.0",
    "org.julienrf"          %% "play-json-derived-codecs" % "4.0.1"
  )

  def test(scope: String = "test") = Seq(
    "uk.gov.hmrc" %% "service-integration-test" % "0.13.0-play-27" % scope,
    "org.pegdown"            % "pegdown"             % "1.6.0"             % scope,
    "org.jsoup"              % "jsoup"               % "1.11.3"            % scope,
    "com.typesafe.play"      %% "play-test"          % PlayVersion.current % scope,
    "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.3"             % scope,
    "com.github.tomakehurst" % "wiremock-jre8"       % "2.26.3"            % scope
  )
}
