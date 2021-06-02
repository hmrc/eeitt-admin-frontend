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
    "org.julienrf"          %% "play-json-derived-codecs"   % "9.0.0",
    "com.47deg"             %% "github4s"                   % "0.28.4",
    "org.http4s"            %% "http4s-blaze-client"        % "0.21.22",
    "io.circe"              %% "circe-core"                 % "0.13.0",
    "io.circe"              %% "circe-parser"               % "0.13.0",
    "io.circe"              %% "circe-optics"               % "0.13.0",
    "com.github.jwt-scala"  %% "jwt-circe"                  % "7.1.4",
    "org.bouncycastle"       % "bcpkix-jdk15on"             % "1.68"
  )

  def test(scope: String = "test") =
    Seq(
      "uk.gov.hmrc"            %% "service-integration-test"    % "0.13.0-play-27"    % scope,
      "org.pegdown"             % "pegdown"                     % "1.6.0"             % scope,
      "org.jsoup"               % "jsoup"                       % "1.11.3"            % scope,
      "com.typesafe.play"      %% "play-test"                   % PlayVersion.current % scope,
      "org.scalatestplus.play" %% "scalatestplus-play"          % "4.0.3"             % scope,
      "com.github.tomakehurst"  % "wiremock-jre8"               % "2.26.3"            % scope,
      "org.scalamock"          %% "scalamock-scalatest-support" % "3.6.0"             % scope
    )
}
