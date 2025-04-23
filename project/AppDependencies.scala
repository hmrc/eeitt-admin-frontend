import sbt._
import play.sbt.PlayImport._
import play.core.PlayVersion

object AppDependencies {

  val jacksonVersion = "2.16.1"
  val jacksonDatabindVersion = "2.16.1"
  val http4sVersion = "0.21.22"
  val circeVersion = "0.13.0"
  val bootstrapVersion = "9.11.0"

  val compile = Seq(
    ws,
    "uk.gov.hmrc"              %% "bootstrap-frontend-play-30"   % bootstrapVersion,
    "uk.gov.hmrc"              %% "play-partials-play-30"        % "9.1.0",
    "uk.gov.hmrc"              %% "play-frontend-hmrc-play-30"   % "9.11.0",
    "uk.gov.hmrc"              %% "internal-auth-client-play-30" % "4.0.0",
    "uk.gov.hmrc.mongo"        %% "hmrc-mongo-play-30"           % "2.6.0",
    "com.github.pureconfig"    %% "pureconfig"                   % "0.17.6",
    "org.typelevel"            %% "cats-core"                    % "2.10.0",
    "org.julienrf"             %% "play-json-derived-codecs"     % "11.0.0",
    "com.dripower"             %% "play-circe"                   % "2814.1",
    "com.47deg"                %% "github4s"                     % "0.28.4",
    "org.http4s"               %% "http4s-blaze-client"          % http4sVersion,
    "org.http4s"               %% "http4s-circe"                 % http4sVersion,
    "io.circe"                 %% "circe-core"                   % circeVersion,
    "io.circe"                 %% "circe-parser"                 % circeVersion,
    "io.circe"                 %% "circe-optics"                 % circeVersion,
    "com.github.jwt-scala"     %% "jwt-circe"                    % "7.1.4",
    "org.bouncycastle"          % "bcpkix-jdk15on"               % "1.70",
    "io.github.java-diff-utils" % "java-diff-utils"              % "4.12",
    "commons-io"                % "commons-io"                   % "2.16.1",
    "org.jetbrains"             % "markdown"                     % "0.1.46",
    "commons-codec"             % "commons-codec"                % "1.17.0",
    // Taken from: https://github.com/orgs/playframework/discussions/11222
    "com.fasterxml.jackson.core"       % "jackson-core"                    % jacksonVersion,
    "com.fasterxml.jackson.core"       %  "jackson-annotations"            % jacksonVersion,
    "com.fasterxml.jackson.datatype"   %  "jackson-datatype-jdk8"          % jacksonVersion,
    "com.fasterxml.jackson.datatype"   %  "jackson-datatype-jsr310"        % jacksonVersion,
    "com.fasterxml.jackson.core"       %  "jackson-databind"               % jacksonDatabindVersion,
    "com.fasterxml.jackson.dataformat" %  "jackson-dataformat-cbor"        % jacksonVersion,
    "com.fasterxml.jackson.module"     %  "jackson-module-parameter-names" % jacksonVersion,
    "com.fasterxml.jackson.module"     %% "jackson-module-scala"           % jacksonVersion
  )

  def test(scope: String = "test") =
    Seq(
      "uk.gov.hmrc"            %% "bootstrap-test-play-30"      % bootstrapVersion    % scope,
      "org.pegdown"             % "pegdown"                     % "1.6.0"             % scope,
      "org.jsoup"               % "jsoup"                       % "1.17.2"            % scope,
      "org.playframework"      %% "play-test"                   % PlayVersion.current % scope,
      "org.scalatestplus.play" %% "scalatestplus-play"          % "7.0.1"             % scope,
      "com.github.tomakehurst"  % "wiremock-jre8"               % "3.0.1"             % scope,
      "org.scalamock"          %% "scalamock"                   % "6.0.0"             % scope,
      "org.scalatest"          %% "scalatest"                   % "3.2.18"            % scope,
      "com.vladsch.flexmark"    % "flexmark-all"                % "0.64.8"            % scope
    )
}
