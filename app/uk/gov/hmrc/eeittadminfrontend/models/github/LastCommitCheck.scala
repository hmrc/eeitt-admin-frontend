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

import cats.syntax.eq._
import github4s.domain.Commit
import uk.gov.hmrc.eeittadminfrontend.deployment.CommitSha
import uk.gov.hmrc.eeittadminfrontend.services.CacheStatus

sealed trait LastCommitCheck extends Product with Serializable {
  def fold[B](f: LastCommitCheck.UpToDate => B)(g: LastCommitCheck.OutOfDate => B): B =
    this match {
      case d: LastCommitCheck.UpToDate  => f(d)
      case d: LastCommitCheck.OutOfDate => g(d)
    }
}

object LastCommitCheck {

  def apply(cacheCommit: CacheStatus.Refreshed, lastCommit: Commit): LastCommitCheck =
    if (cacheCommit.commit.sha === lastCommit.sha)
      UpToDate(cacheCommit)
    else
      OutOfDate(cacheCommit, lastCommit)

  case class UpToDate(cacheCommit: CacheStatus.Refreshed) extends LastCommitCheck {
    def commitSha: CommitSha = CommitSha(cacheCommit.commit.sha)
  }
  case class OutOfDate(cacheCommit: CacheStatus.Refreshed, lastCommit: Commit) extends LastCommitCheck
}
