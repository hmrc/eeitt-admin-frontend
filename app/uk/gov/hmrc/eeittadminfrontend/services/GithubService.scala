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
import github4s.domain.{ Commit, Content, RefCommit }
import io.circe.{ DecodingFailure, Json }
import org.apache.commons.codec.binary.Base64
import org.slf4j.{ Logger, LoggerFactory }
import uk.gov.hmrc.eeittadminfrontend.connectors.GithubConnector
import uk.gov.hmrc.eeittadminfrontend.deployment.{ BlobSha, CommitSha, Filename }
import uk.gov.hmrc.eeittadminfrontend.models.FormTemplateId

class GithubService(githubConnector: GithubConnector) {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  def listTemplates: EitherT[IO, String, NonEmptyList[Content]] = EitherT(githubConnector.listTemplates)

  def lastCommit: EitherT[IO, String, Commit] = EitherT(githubConnector.lastCommit)

  def getCommitByFilename(filename: Filename): EitherT[IO, String, Commit] = EitherT(
    githubConnector.getCommitByFilename(filename)
  )

  def getCommit(commitSha: CommitSha): EitherT[IO, String, RefCommit] = EitherT(githubConnector.getCommit(commitSha))

  def retrieveFormTemplate(sha: BlobSha): EitherT[IO, String, (FormTemplateId, Json)] = getBlob(sha).flatMap { json =>
    logger.debug(s"Downloading url blob $sha from Github Completed")
    val hcursor = json.hcursor
    val formTemplateId: Either[DecodingFailure, FormTemplateId] =
      hcursor.downField("_id").as[String].map(FormTemplateId.apply)

    val githubContent: Either[String, (FormTemplateId, Json)] = formTemplateId.bimap(
      _.getMessage(),
      formTemplateId => formTemplateId -> json
    )
    EitherT.fromEither(githubContent)
  }

  def getBlob(sha: BlobSha): EitherT[IO, String, Json] = EitherT(githubConnector.getBlob(sha)).flatMap { blobContent =>
    blobContent.content.fold(EitherT.leftT[IO, Json](s"No blob content available for sha: $sha")) { content =>
      val rawJson = new String(Base64.decodeBase64(content), "UTF-8")
      val json: Either[String, Json] = io.circe.parser.parse(rawJson).leftMap(_.message)
      EitherT.fromEither(json)
    }
  }
}
