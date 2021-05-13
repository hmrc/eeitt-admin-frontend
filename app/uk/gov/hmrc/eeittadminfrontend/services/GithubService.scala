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

import cats.data.EitherT
import cats.effect.IO
import github4s.domain.{ Committer, WriteFileResponse }
import io.circe.Json
import play.api.mvc.{ Result, Results }
import uk.gov.hmrc.eeittadminfrontend.connectors.GithubConnector
import uk.gov.hmrc.eeittadminfrontend.models.FormTemplateId
import uk.gov.hmrc.eeittadminfrontend.models.github.GetTemplate

class GithubService(maybeGithubConnector: Option[GithubConnector]) {

  def getTemplate(formTemplateId: FormTemplateId): EitherT[IO, String, GetTemplate] = {
    val noGithubEnabled: EitherT[IO, String, GetTemplate] = EitherT.pure(GetTemplate.NoGithubEnabled(formTemplateId))
    maybeGithubConnector.fold(noGithubEnabled)(githubConnector => EitherT(githubConnector.getTemplate(formTemplateId)))
  }

  def updateFormTemplateId(
    getTemplate: GetTemplate,
    template: Json,
    author: Committer
  ): EitherT[IO, String, Result] = {

    val noGithubEnabled: EitherT[IO, String, Result] = EitherT.pure(Results.Ok("Saved"))

    maybeGithubConnector.fold(noGithubEnabled)(githubConnector =>
      getTemplate
        .fold(newFile => toResult(githubConnector.createFile(newFile.formTemplateId, template, author)))(existing =>
          toResult(githubConnector.updateFile(existing.content, template, author))
        )(_ => noGithubEnabled)
    )
  }

  private def toResult[A](writeFileResponseIO: IO[Either[String, WriteFileResponse]]): EitherT[IO, String, Result] =
    EitherT(writeFileResponseIO)
      .map(writeFileResponse => Results.Redirect(writeFileResponse.commit.html_url))
}
