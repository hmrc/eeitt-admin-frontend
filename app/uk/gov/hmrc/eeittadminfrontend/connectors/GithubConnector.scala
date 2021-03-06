/*
 * Copyright 2021 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.eeittadminfrontend.connectors

import cats.data.NonEmptyList
import cats.effect.{ Blocker, ContextShift, IO }
import github4s.{ GHError, GHResponse, Github }
import github4s.Decoders._
import github4s.domain.{ BlobContent, Commit, Content, Pagination, RefCommit }
import java.net.Proxy
import java.util.concurrent.Executors
import org.http4s.{ Header, Request, Uri }
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.client.{ Client, JavaNetClientBuilder }
import org.slf4j.{ Logger, LoggerFactory }
import scala.concurrent.ExecutionContext.global
import uk.gov.hmrc.eeittadminfrontend.deployment.{ BlobSha, CommitSha, Filename }
import uk.gov.hmrc.eeittadminfrontend.wshttp.WSHttp
import uk.gov.hmrc.eeittadminfrontend.models.github.Authorization

class GithubConnector(authorization: Authorization, maybeProxy: Option[Proxy], wsHttp: WSHttp) {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  val repoOwner = authorization.repoOwner
  val repoName = authorization.repoName
  val master = Some("master")

  implicit val cs: ContextShift[IO] = IO.contextShift(global)
  val httpClient: Client[IO] = {
    val blockingPool = Executors.newFixedThreadPool(5)
    val blocker = Blocker.liftExecutorService(blockingPool)
    val cb = JavaNetClientBuilder[IO](blocker)

    maybeProxy.fold(cb)(cb.withProxy).create // use BlazeClientBuilder for production use
  }

  val gh = Github[IO](httpClient, Some(authorization.accessToken))

  def lastCommit: IO[Either[String, Commit]] =
    gh.repos.listCommits(repoOwner, repoName, pagination = Some(Pagination(1, 1))).map { response =>
      response.result match {
        case Right(r) => Right(r.head)
        case Left(e) =>
          logError(s"Error when fetching last commit: ${e.getMessage}", e)
      }
    }

  def getCommit(commitSha: CommitSha): IO[Either[String, RefCommit]] =
    gh.gitData.getCommit(repoOwner, repoName, commitSha.value).map { response =>
      response.result match {
        case Right(r) => Right(r)
        case Left(e) =>
          logError(s"Error when fetching commit ${commitSha.value}: ${e.getMessage}", e)
      }
    }

  def getCommitByFilename(filename: Filename): IO[Either[String, Commit]] = {
    // We can't use gh.repos.listCommits() since it is not handling '&' character in filename correctly.
    val uri = Uri(
      scheme = Some(Uri.Scheme.https),
      authority = Some(Uri.Authority(host = Uri.RegName("api.github.com"))),
      path = s"repos/$repoOwner/$repoName/commits"
    ).withQueryParam("path", filename.value)

    httpClient
      .expect[List[Commit]](
        Request[IO]()
          .withUri(uri)
          .withHeaders(List(Header("Authorization", s"Bearer ${authorization.accessToken}")): _*)
      )
      .attempt
      .map {
        case Right(commit :: _) => Right(commit)
        case Right(Nil)         => logError(s"No commit found for $filename")
        case Left(e) =>
          logError(s"Failed to query commits $uri because ${e.getMessage} ", e)
      }
  }

  def listTemplates(): IO[Either[String, NonEmptyList[Content]]] = {

    logger.debug(s"Fetching list of templates from Github")

    val searchResults: IO[GHResponse[NonEmptyList[Content]]] =
      gh.repos.getContents(repoOwner, repoName, ".", master)

    searchResults.map { response =>
      response.result match {
        case Right(r) =>
          val jsonFiles: List[Content] = r.filter { content =>
            content.name.endsWith(".json")
          }
          val maybeJsons: Option[NonEmptyList[Content]] = NonEmptyList.fromList(jsonFiles)
          maybeJsons.fold(logError[NonEmptyList[Content]]("No json file found."))(Right(_))
        case Left(e) =>
          logError(s"Templates search failed because ${e.getMessage}", e)
      }
    }
  }

  def getBlob(sha: BlobSha): IO[Either[String, BlobContent]] =
    gh.gitData.getBlob(repoOwner, repoName, sha.value).map { response =>
      response.result match {
        case Right(r) => Right(r)
        case Left(e)  => logError(s"Cannot download blob $sha: ${e.getMessage}", e)
      }
    }

  private def logError[A](errorMessage: String, e: Throwable): Either[String, A] = {
    logger.error(errorMessage, e)
    logError(errorMessage)
  }
  private def logError[A](errorMessage: String, e: GHError): Either[String, A] = {
    logger.error(errorMessage, e)
    logError(errorMessage)
  }
  private def logError[A](errorMessage: String): Either[String, A] = {
    logger.error(errorMessage)
    Left(errorMessage)
  }
}
