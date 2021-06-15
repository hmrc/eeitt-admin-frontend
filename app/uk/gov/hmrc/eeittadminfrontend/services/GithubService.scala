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

package uk.gov.hmrc.eeittadminfrontend.services

import cats.syntax.either._
import cats.data.{ EitherT, NonEmptyList }
import cats.effect.IO
import github4s.domain.{ Commit, Content }
import io.circe.{ DecodingFailure, Json }
import uk.gov.hmrc.eeittadminfrontend.connectors.GithubConnector
import uk.gov.hmrc.eeittadminfrontend.deployment.{ DownloadUrl, Filename, GithubContent }
import uk.gov.hmrc.eeittadminfrontend.models.FormTemplateId

class GithubService(maybeGithubConnector: Option[GithubConnector]) {

  private def withGithubConnector[A](f: GithubConnector => EitherT[IO, String, A]): EitherT[IO, String, A] = {
    val noGithubEnabled: EitherT[IO, String, A] =
      EitherT.leftT[IO, A]("No Github integration available. Check service config.")
    maybeGithubConnector.fold(noGithubEnabled)(f)
  }

  def listTemplates(): EitherT[IO, String, NonEmptyList[Content]] =
    withGithubConnector { githubConnector =>
      EitherT(githubConnector.listTemplates)
    }

  def getCommit(filename: Filename): EitherT[IO, String, Commit] =
    withGithubConnector { githubConnector =>
      EitherT(githubConnector.getCommit(filename.value))
    }

  def retrieveFilename(filename: Filename): EitherT[IO, String, Content] =
    withGithubConnector { githubConnector =>
      EitherT(githubConnector.fetchFilenameContent(filename))
    }

  def retrieveFilenameData(filename: Filename): EitherT[IO, String, (DownloadUrl, GithubContent)] =
    retrieveFilename(filename)
      .flatMap { content =>
        EitherT.fromEither[IO](DownloadUrl.fromContent(content)).flatMap { downloadUrl =>
          retrieveFormTemplate(downloadUrl).map((downloadUrl, _))
        }
      }

  def retrieveFormTemplate(downloadUrl: DownloadUrl): EitherT[IO, String, GithubContent] =
    withGithubConnector { githubConnector =>
      val jsonContent: EitherT[IO, String, Json] = EitherT(githubConnector.fetchDownloadUrl(downloadUrl))

      jsonContent.flatMap { json =>
        val hcursor = json.hcursor
        val formTemplateId: Either[DecodingFailure, FormTemplateId] =
          hcursor.downField("_id").as[String].map(FormTemplateId.apply)

        val githubContent: Either[String, GithubContent] = formTemplateId.bimap(
          _.getMessage(),
          formTemplateId => GithubContent(formTemplateId, json)
        )
        EitherT.fromEither(githubContent)
      }
    }
}
