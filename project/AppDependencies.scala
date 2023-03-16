import sbt._
import play.sbt.PlayImport._
import play.core.PlayVersion

object AppDependencies {

  val http4sVersion = "0.21.22"
  val circeVersion = "0.13.0"

  val compile = Seq(
    ws,
    "uk.gov.hmrc"              %% "bootstrap-frontend-play-28"   % "7.13.0",
    "uk.gov.hmrc"              %% "play-partials"                % "8.3.0-play-28",
    "uk.gov.hmrc"              %% "play-frontend-hmrc"           % "6.8.0-play-28",
    "uk.gov.hmrc"              %% "internal-auth-client-play-28" % "1.0.0",
    "org.reactivemongo"        %% "play2-reactivemongo"          % "1.0.4-play28",
    "com.github.pureconfig"    %% "pureconfig"                   % "0.15.0",
    "org.typelevel"            %% "cats-core"                    % "2.6.1",
    "org.julienrf"             %% "play-json-derived-codecs"     % "10.0.2",
    "com.47deg"                %% "github4s"                     % "0.28.4",
    "org.http4s"               %% "http4s-blaze-client"          % http4sVersion,
    "org.http4s"               %% "http4s-circe"                 % http4sVersion,
    "io.circe"                 %% "circe-core"                   % circeVersion,
    "io.circe"                 %% "circe-parser"                 % circeVersion,
    "io.circe"                 %% "circe-optics"                 % circeVersion,
    "com.github.jwt-scala"     %% "jwt-circe"                    % "7.1.4",
    "org.bouncycastle"          % "bcpkix-jdk15on"               % "1.68",
    "io.github.java-diff-utils" % "java-diff-utils"              % "4.10",
    "commons-io"                % "commons-io"                   % "2.11.0"
  )

  def test(scope: String = "test") =
    Seq(
      "uk.gov.hmrc"            %% "bootstrap-test-play-28"      % "7.13.0"            % scope,
      "uk.gov.hmrc"            %% "service-integration-test"    % "1.3.0-play-28"     % scope,
      "org.pegdown"             % "pegdown"                     % "1.6.0"             % scope,
      "org.jsoup"               % "jsoup"                       % "1.11.3"            % scope,
      "com.typesafe.play"      %% "play-test"                   % PlayVersion.current % scope,
      "org.scalatestplus.play" %% "scalatestplus-play"          % "5.1.0"             % scope,
      "com.github.tomakehurst"  % "wiremock-jre8"               % "2.26.3"            % scope,
      "org.scalamock"          %% "scalamock"                   % "5.2.0"             % scope,
      "org.scalatest"          %% "scalatest"                   % "3.2.0"             % scope,
      "com.vladsch.flexmark"    % "flexmark-all"                % "0.35.10"           % scope
    )
}
