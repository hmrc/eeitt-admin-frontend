/*
 * Copyright 2023 HM Revenue & Customs
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

import java.util.concurrent.Executors
import javax.inject.Inject
import org.http4s.{ Header, Request, Uri }
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.client.{ Client, JavaNetClientBuilder }
import org.slf4j.{ Logger, LoggerFactory }
import uk.gov.hmrc.eeittadminfrontend.deployment.GithubPath.asPath

import scala.concurrent.ExecutionContext
import uk.gov.hmrc.eeittadminfrontend.deployment.{ BlobSha, CommitSha, GithubPath }
import uk.gov.hmrc.eeittadminfrontend.models.github.Authorization
import uk.gov.hmrc.eeittadminfrontend.proxy.ProxyProvider

class GithubConnector @Inject() (authorization: Authorization, proxyProvider: ProxyProvider)(implicit
  ec: ExecutionContext
) {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  val repoOwner = authorization.repoOwner
  val repoName = authorization.repoName
  val main = Some("main")
  val githubExtensions = List(".json", ".hbs")

  implicit val cs: ContextShift[IO] = IO.contextShift(ec)
  val httpClient: Client[IO] = {
    val blockingPool = Executors.newFixedThreadPool(5)
    val blocker = Blocker.liftExecutorService(blockingPool)
    val cb = JavaNetClientBuilder[IO](blocker)

    proxyProvider.maybeProxy.fold(cb)(cb.withProxy).create // use BlazeClientBuilder for production use
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

  def getCommitByPath(githubPath: GithubPath): IO[Either[String, Commit]] = {
    // We can't use gh.repos.listCommits() since it is not handling '&' character in filename correctly.
    val uri = Uri(
      scheme = Some(Uri.Scheme.https),
      authority = Some(Uri.Authority(host = Uri.RegName("api.github.com"))),
      path = s"repos/$repoOwner/$repoName/commits"
    ).withQueryParam("path", asPath(githubPath))

    httpClient
      .expect[List[Commit]](
        Request[IO]()
          .withUri(uri)
          .withHeaders(List(Header("Authorization", s"Bearer ${authorization.accessToken}")): _*)
      )
      .attempt
      .map {
        case Right(commit :: _) => Right(commit)
        case Right(Nil)         => logError(s"No commit found for $githubPath")
        case Left(e) =>
          logError(s"Failed to query commits $uri because ${e.getMessage} ", e)
      }
  }

  def listTemplates(): IO[Either[String, NonEmptyList[Content]]] = {

    logger.debug(s"Fetching list of templates from Github")

    def combineGHResponses(
      response1: GHResponse[NonEmptyList[Content]],
      response2: GHResponse[NonEmptyList[Content]]
    ): GHResponse[NonEmptyList[Content]] =
      (response1.result, response2.result) match {
        case (Right(result1), Right(result2)) =>
          GHResponse(Right(result1.concat(result2.toList)), response1.statusCode, response1.headers)
        case (Right(_), Left(_)) => // 2. folder is empty
          response1
        case (Left(error1), _) =>
          GHResponse(Left(error1), response1.statusCode, response1.headers)
      }

    val searchResults: IO[GHResponse[NonEmptyList[Content]]] = for {
      resultForRoot              <- gh.repos.getContents(repoOwner, repoName, ".", main)
      resultForHandleBars        <- gh.repos.getContents(repoOwner, repoName, "handlebars", main)
      resultForHandleBarsSchemas <- gh.repos.getContents(repoOwner, repoName, "jsonSchemas", main)
    } yield {
      val combineWithHandleBars = combineGHResponses(resultForRoot, resultForHandleBars)
      combineGHResponses(combineWithHandleBars, resultForHandleBarsSchemas)
    }

    searchResults.map { response =>
      response.result match {
        case Right(r) =>
          val jsonFiles: List[Content] = r.filter { content =>
            githubExtensions.exists(content.name.endsWith)
          }
          val maybeJsons: Option[NonEmptyList[Content]] = NonEmptyList.fromList(jsonFiles)
          maybeJsons.fold(logError[NonEmptyList[Content]]("No json/hbs file found."))(Right(_))
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
