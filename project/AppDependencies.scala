import sbt._
import play.sbt.PlayImport._
import play.core.PlayVersion

object AppDependencies {

  val compile = Seq(
    ws,
    "uk.gov.hmrc"           %% "bootstrap-frontend-play-27" % "4.3.0",
    "uk.gov.hmrc"           %% "play-partials"              % "8.1.0-play-27",
    "uk.gov.hmrc"           %% "play-frontend-govuk"        % "0.71.0-play-27",
    "uk.gov.hmrc"           %% "play-frontend-hmrc"         % "0.60.0-play-27",
    "com.github.pureconfig" %% "pureconfig"                 % "0.15.0",
    "org.typelevel"         %% "cats-core"                  % "2.6.0",
    "org.julienrf"          %% "play-json-derived-codecs"   % "9.0.0"
  )

  def test(scope: String = "test") =
    Seq(
      "uk.gov.hmrc"            %% "service-integration-test" % "0.13.0-play-27"    % scope,
      "org.pegdown"             % "pegdown"                  % "1.6.0"             % scope,
      "org.jsoup"               % "jsoup"                    % "1.11.3"            % scope,
      "com.typesafe.play"      %% "play-test"                % PlayVersion.current % scope,
      "org.scalatestplus.play" %% "scalatestplus-play"       % "4.0.3"             % scope,
      "com.github.tomakehurst"  % "wiremock-jre8"            % "2.26.3"            % scope
    )
}
