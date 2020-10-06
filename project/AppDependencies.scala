import sbt._
import play.sbt.PlayImport._
import play.core.PlayVersion

object AppDependencies {

  val compile = Seq(
    ws,
    "uk.gov.hmrc"           %% "bootstrap-play-26"        % "1.3.0",
    "uk.gov.hmrc"           %% "play-partials"            % "6.9.0-play-26",
    "uk.gov.hmrc"           %% "play-frontend-govuk"      % "0.48.0-play-26",
    "uk.gov.hmrc"           %% "play-frontend-hmrc"       % "0.15.0-play-26",
    "com.github.pureconfig" %% "pureconfig"               % "0.10.0",
    "org.typelevel"         %% "cats-core"                % "1.6.0",
    "org.julienrf"          %% "play-json-derived-codecs" % "4.0.1"
  )

  def test(scope: String = "test") = Seq(
    "uk.gov.hmrc"            %% "bootstrap-play-26"  % "1.3.0"             % Test classifier "tests",
    "uk.gov.hmrc"            %% "hmrctest"           % "3.9.0-play-26"     % scope,
    "org.pegdown"            % "pegdown"             % "1.6.0"             % scope,
    "org.jsoup"              % "jsoup"               % "1.11.3"            % scope,
    "com.typesafe.play"      %% "play-test"          % PlayVersion.current % scope,
    "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.3"             % scope,
    "com.github.tomakehurst" % "wiremock-jre8"       % "2.26.3"            % scope
  )
}
