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

package uk.gov.hmrc.eeittadminfrontend.models.github

import play.api.Configuration

case class Authorization(
  repoOwner: String,
  repoName: String,
  accessToken: String
)

object Authorization {
  def fromConfig(configuration: Configuration): Authorization = {
    val repoOwner = configuration.get[String]("github.repo-owner")
    val repoName = configuration.get[String]("github.repo-name")
    val accessToken = configuration.get[String]("github.access-token")

    Authorization(repoOwner, repoName, accessToken)
  }
}
