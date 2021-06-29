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

package uk.gov.hmrc.eeittadminfrontend.deployment

import play.twirl.api.Html
import uk.gov.hmrc.eeittadminfrontend.models.github.Authorization
import uk.gov.hmrc.govukfrontend.views.html.components.{ HtmlContent, TableRow, Text }
import uk.gov.hmrc.eeittadminfrontend.utils.DateUtils

sealed trait DeploymentDiff extends Product with Serializable {
  def fold[B](f: DeploymentDiff.None.type => B)(g: DeploymentDiff.Single => B)(h: DeploymentDiff.Double => B): B =
    this match {
      case d: DeploymentDiff.None.type => f(d)
      case d: DeploymentDiff.Single    => g(d)
      case d: DeploymentDiff.Double    => h(d)
    }

  private val emptySha = (BlobSha(""), BlobSha(""))

  def toSha: (BlobSha, BlobSha) =
    fold(_ => emptySha)(_ => emptySha)(double => (double.dr1.blobSha, double.dr2.blobSha))

  def toTableRow(
    authorization: Authorization,
    highlightMinus: BlobSha,
    highlightPlus: BlobSha
  ): Seq[TableRow] =
    fold(_ => noDeployment)(singleDeployment(authorization, highlightMinus))(
      doubleDeployment(authorization, highlightMinus, highlightPlus)
    )

  private def noDeployment: Seq[TableRow] = Seq(
    TableRow(
      content = Text("No deployment yet"),
      colspan = Some(6)
    )
  )

  private def singleDeployment(authorization: Authorization, highlightMinus: BlobSha)(
    single: DeploymentDiff.Single
  ): Seq[TableRow] = {
    val deploymentRecord = single.dr

    val shaRow = uk.gov.hmrc.eeittadminfrontend.views.html.deployment_sha(deploymentRecord.blobSha)

    val rows = commonColumns(authorization, deploymentRecord, shaRow, deploymentRecord.blobSha)

    rows.map(
      _.copy(classes =
        if (highlightMinus == deploymentRecord.blobSha) "highlightMinus"
        else ""
      )
    )
  }

  private def doubleDeployment(authorization: Authorization, highlightMinus: BlobSha, highlightPlus: BlobSha)(
    dd: DeploymentDiff.Double
  ): Seq[TableRow] = {
    val deploymentRecord = dd.dr2
    val sha1 = dd.dr1.blobSha
    val sha2 = dd.dr2.blobSha

    val shaRow =
      uk.gov.hmrc.eeittadminfrontend.views.html
        .deployment_link_diff(deploymentRecord.formTemplateId, sha1, sha2)

    val rows = commonColumns(authorization, deploymentRecord, shaRow, sha2)

    rows.map(
      _.copy(classes =
        if (highlightPlus == sha2) "highlightPlus"
        else if (highlightMinus == sha2) "highlightMinus"
        else ""
      )
    )
  }

  private def commonColumns(
    authorization: Authorization,
    deploymentRecord: DeploymentRecord,
    shaContent: Html,
    blobSha: BlobSha
  ) = Seq(
    TableRow(
      content = Text(deploymentRecord.username.value)
    ),
    TableRow(
      content = Text(DateUtils.formatInstant(deploymentRecord.createdAt))
    ),
    TableRow(
      content = HtmlContent(
        uk.gov.hmrc.eeittadminfrontend.views.html
          .deployment_link_github_commit(authorization, deploymentRecord.commitSha)
      )
    ),
    TableRow(
      content = HtmlContent(
        uk.gov.hmrc.eeittadminfrontend.views.html
          .deployment_link_github_commit_blob(authorization, deploymentRecord.commitSha, deploymentRecord.filename)
      )
    ),
    TableRow(
      content = HtmlContent(shaContent)
    )
  )
}

object DeploymentDiff {
  case object None extends DeploymentDiff
  case class Single(dr: DeploymentRecord) extends DeploymentDiff
  case class Double(dr1: DeploymentRecord, dr2: DeploymentRecord) extends DeploymentDiff

}
