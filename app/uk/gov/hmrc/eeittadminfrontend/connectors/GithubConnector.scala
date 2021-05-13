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

import github4s.domain.Committer
import github4s.{ GHError, GHResponse }
import github4s.domain.{ Content, WriteFileResponse }
import io.circe._
import cats.data.NonEmptyList
import org.slf4j.{ Logger, LoggerFactory }
import uk.gov.hmrc.eeittadminfrontend.models.FormTemplateId
import uk.gov.hmrc.eeittadminfrontend.wshttp.WSHttp
import uk.gov.hmrc.eeittadminfrontend.models.github.{ Authorization, GetTemplate, PrettyPrintJson }

class GithubConnector(authorization: Authorization, wsHttp: WSHttp) {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  val repoOwner = authorization.repoOwner
  val repoName = authorization.repoName
  val branch = Some(authorization.branch)

  import java.util.concurrent.Executors

  import cats.effect.{ Blocker, ContextShift, IO }
  import github4s.Github
  import org.http4s.client.{ Client, JavaNetClientBuilder }

  import scala.concurrent.ExecutionContext.global

  val httpClient: Client[IO] = {
    val blockingPool = Executors.newFixedThreadPool(5)
    val blocker = Blocker.liftExecutorService(blockingPool)
    implicit val cs: ContextShift[IO] = IO.contextShift(global)
    JavaNetClientBuilder[IO](blocker).create // use BlazeClientBuilder for production use
  }

  val gh = Github[IO](httpClient, Some(authorization.accessToken))

  def createFile(
    formTemplateId: FormTemplateId,
    template: Json,
    author: Committer
  ): IO[Either[String, WriteFileResponse]] = {

    val filename = formTemplateId + ".json"
    val message = s"Creating $filename - ${author.name}"
    val formatted = PrettyPrintJson(template)

    val getContents: IO[GHResponse[WriteFileResponse]] =
      gh.repos.createFile(
        repoOwner,
        repoName,
        filename,
        message,
        formatted.content,
        branch
      )

    getContents.map { response =>
      response.result match {
        case Right(r) => Right(r)
        case Left(e) =>
          logError(s"We could not create file $filename because ${e.getMessage}", e)
      }
    }
  }

  def updateFile(fileContent: Content, template: Json, author: Committer): IO[Either[String, WriteFileResponse]] = {

    val filename = fileContent.name
    val formatted = PrettyPrintJson(template)
    val message = s"Updating ${fileContent.name} - ${author.name}"
    val content = formatted.content
    val sha = fileContent.sha

    val getContents: IO[GHResponse[WriteFileResponse]] =
      gh.repos.updateFile(
        repoOwner,
        repoName,
        filename,
        message,
        content,
        sha,
        branch
      )

    getContents.map { response =>
      response.result match {
        case Right(r) => Right(r)
        case Left(e) =>
          logError(s"We could not update file $filename because ${e.getMessage}", e)
      }
    }
  }

  def getTemplate(
    formTemplateId: FormTemplateId
  ): IO[Either[String, GetTemplate]] = {

    val filename = formTemplateId + ".json"

    val getContents: IO[GHResponse[NonEmptyList[Content]]] =
      gh.repos.getContents(repoOwner, repoName, filename, branch)

    getContents.map { response =>
      response.result match {
        case Right(NonEmptyList(head, Nil))    => Right(GetTemplate.Exists(formTemplateId, head))
        case Right(NonEmptyList(head, tail))   => Left(s"More than one file found. It looks like $filename is a folder.")
        case Left(GHError.NotFoundError(_, _)) => Right(GetTemplate.NewFile(formTemplateId))
        case Left(eh)                          => logError(s"Error when retrieving /contents/$filename: $eh", eh)
      }
    }
  }

  private def logError[A](errorMessage: String, e: GHError): Either[String, A] = {
    logger.error(errorMessage, e)
    Left(errorMessage)
  }
}
