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

package uk.gov.hmrc.eeittadminfrontend.models.github

import play.api.Configuration
import java.util.Base64

case class Authorization(
  repoOwner: String,
  repoName: String,
  branch: String,
  accessToken: String
)

object Authorization {
  def apply(configuration: Configuration): Option[Authorization] = for {
    repoOwner   <- configuration.getOptional[String]("github.repo-owner")
    repoName    <- configuration.getOptional[String]("github.repo-name")
    branch      <- configuration.getOptional[String]("github.branch")
    accessToken <- configuration.getOptional[String]("github.access-token")
  } yield Authorization(repoOwner, repoName, branch, decode(accessToken))

  private def decode(string: String): String =
    new String(Base64.getDecoder.decode(string), "UTF-8")

}
