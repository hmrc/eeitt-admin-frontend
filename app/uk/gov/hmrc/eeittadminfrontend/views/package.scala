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

package uk.gov.hmrc.eeittadminfrontend

import github4s.domain.Commit
import uk.gov.hmrc.eeittadminfrontend.deployment.CommitSha
import uk.gov.hmrc.eeittadminfrontend.models.github.Authorization
import uk.gov.hmrc.eeittadminfrontend.utils.DateUtils
import uk.gov.hmrc.govukfrontend.views.html.components._

import java.time.{ Instant, LocalDateTime, ZoneId }
import java.time.format.DateTimeFormatter

package object views {

  private val dtf = DateTimeFormatter
    .ofPattern("dd MMM yyyy HH:mm:ss")

  def formatLocalDate(localDateTime: LocalDateTime): String = dtf.format(localDateTime)
  def formatInstant(instant: Instant): String = dtf.format(instant.atZone(ZoneId.of("UTC")).toLocalDateTime)

  def commitToTable(authorization: Authorization, commit: Commit) =
    Table(
      rows = Seq(
        Seq(
          TableRow(
            content = Text(commit.login.getOrElse("Unknown login"))
          ),
          TableRow(
            content = Text(DateUtils.formatAsInstant(commit.date))
          ),
          TableRow(
            content = Text(commit.message)
          ),
          TableRow(
            content = HtmlContent(
              uk.gov.hmrc.eeittadminfrontend.views.html
                .deployment_link_github_commit(authorization, CommitSha(commit.sha))
            )
          )
        )
      ),
      head = Some(
        Seq(
          HeadCell(
            content = Text("Author")
          ),
          HeadCell(
            content = Text("Date")
          ),
          HeadCell(
            content = Text("Message")
          ),
          HeadCell(
            content = Text("Link")
          )
        )
      ),
      firstCellIsHeader = false
    )

}
