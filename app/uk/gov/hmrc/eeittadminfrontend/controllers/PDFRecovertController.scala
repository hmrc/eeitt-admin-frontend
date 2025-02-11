/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.eeittadminfrontend.controllers

import org.slf4j.{ Logger, LoggerFactory }
import play.api.i18n.I18nSupport
import play.api.libs.Files.TemporaryFile
import play.api.mvc.{ Action, AnyContent, MessagesControllerComponents, MultipartFormData }
import uk.gov.hmrc.eeittadminfrontend.connectors.GformFrontendConnector
import uk.gov.hmrc.eeittadminfrontend.models.FormTemplateId
import uk.gov.hmrc.eeittadminfrontend.models.fileupload.EnvelopeId
import uk.gov.hmrc.eeittadminfrontend.views.html.hardcoded.link
import uk.gov.hmrc.govukfrontend.views.Aliases.{ TableRow, Text }
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.HtmlContent
import uk.gov.hmrc.govukfrontend.views.viewmodels.table.{ HeadCell, Table }
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }
import scala.io.Source
import scala.util.Using

class PDFRecovertController @Inject() (
  frontendAuthComponents: FrontendAuthComponents,
  gformFrontendConnector: GformFrontendConnector,
  messagesControllerComponents: MessagesControllerComponents,
  pdf_recovery: uk.gov.hmrc.eeittadminfrontend.views.html.pdf_recovery
)(implicit ec: ExecutionContext)
    extends GformAdminFrontendController(frontendAuthComponents, messagesControllerComponents) with I18nSupport {
  private val logger: Logger = LoggerFactory.getLogger(getClass)

  private val tableHead = Seq(
    HeadCell(Text("SubmissionTime")),
    HeadCell(Text("EnvelopeID")),
    HeadCell(Text("Submission Ref")),
    HeadCell(Text("User Type")),
    HeadCell(Text("FormId")),
    HeadCell(Text("DMS Link")),
    HeadCell(Text("Instruction Link"))
  )

  private val emptyTable = Table(head = Some(tableHead))

  def show() = authorizedRead.async { implicit request =>
    Future.successful(Ok(pdf_recovery(emptyTable)))
  }

  def downloadDms(
    formTemplateId: FormTemplateId,
    envelopeId: EnvelopeId,
    submissionRef: String,
    submissionTime: String,
    affinityGroup: String
  ): Action[AnyContent] =
    authorizedWrite.async { _ =>
      logger.info(
        s"Downloading DMS pdf formTemplateId: ${formTemplateId.value} , envelopeId: ${envelopeId.value}, submissionRef: $submissionRef, submissionTime: $submissionTime"
      )
      val customFormatter = DateTimeFormatter.ofPattern("yyyy-MM-ddHH:mm:ss")
      val localDateTime = LocalDateTime.parse(submissionTime, customFormatter)
      val formatterDate = DateTimeFormatter.ofPattern("yyyyMMdd")
      gformFrontendConnector
        .downloadDmsPdf(formTemplateId, envelopeId, submissionTime, affinityGroup)
        .map {
          case Right(source) =>
            Ok.streamed(source, None)
              .withHeaders(
                CONTENT_TYPE -> "application/pdf",
                CONTENT_DISPOSITION -> s"""filename = "${envelopeId.value}_${submissionRef
                  .replace("-", "")}-${localDateTime.toLocalDate
                  .format(formatterDate)}-iform.pdf""""
              )
          case Left(error) => BadRequest(error)
        }
    }

  def downloadInstruction(
    formTemplateId: FormTemplateId,
    envelopeId: EnvelopeId,
    submissionRef: String,
    submissionTime: String,
    affinityGroup: String
  ): Action[AnyContent] =
    authorizedWrite.async { _ =>
      logger.info(
        s"Downloading instruction pdf formTemplateId: ${formTemplateId.value} , envelopeId: ${envelopeId.value}, submissionRef: $submissionRef, submissionTime: $submissionTime"
      )
      val customFormatter = DateTimeFormatter.ofPattern("yyyy-MM-ddHH:mm:ss")
      val localDateTime = LocalDateTime.parse(submissionTime, customFormatter)
      val formatterDate = DateTimeFormatter.ofPattern("yyyyMMdd")
      gformFrontendConnector
        .downloadInstructionPdf(formTemplateId, envelopeId, submissionTime, affinityGroup)
        .map {
          case Right(source) =>
            Ok.streamed(source, None)
              .withHeaders(
                CONTENT_TYPE -> "application/pdf",
                CONTENT_DISPOSITION -> s"""filename = "${envelopeId.value}_${submissionRef
                  .replace("-", "")}-${localDateTime.toLocalDate
                  .format(formatterDate)}-customerSummary.pdf""""
              )
          case Left(error) => BadRequest(error)
        }
    }

  def uploadCsv(): Action[MultipartFormData[TemporaryFile]] =
    authorizedWrite.async(parse.multipartFormData) { implicit request =>
      if (request.body.file("file").isDefined) {
        val file = TemporaryFile.temporaryFileToFile(request.body.file("file").get.ref)
        logger.info("Reading csv file")

        Using(Source.fromFile(file)) { source =>
          val lines = source.getLines().toList
          val rows: Seq[Seq[TableRow]] = lines.map { line =>
            logger.info(s"Reading line 1: $line")
            line.split(",") match {
              case Array(submissionTime, envelopeId, submissionRef, affinityGroup, formTemplateId) =>
                Seq(
                  TableRow(Text(submissionTime)),
                  TableRow(Text(envelopeId)),
                  TableRow(Text(submissionRef)),
                  TableRow(Text(affinityGroup)),
                  TableRow(Text(formTemplateId)),
                  TableRow(
                    HtmlContent(
                      link(
                        "Download",
                        uk.gov.hmrc.eeittadminfrontend.controllers.routes.PDFRecovertController
                          .downloadDms(
                            FormTemplateId(formTemplateId),
                            EnvelopeId(envelopeId),
                            submissionRef,
                            submissionTime.replace(" ", ""),
                            if (affinityGroup.isEmpty) "individual" else affinityGroup
                          )
                      )
                    )
                  ),
                  TableRow(
                    HtmlContent(
                      link(
                        "Download",
                        uk.gov.hmrc.eeittadminfrontend.controllers.routes.PDFRecovertController
                          .downloadInstruction(
                            FormTemplateId(formTemplateId),
                            EnvelopeId(envelopeId),
                            submissionRef,
                            submissionTime.replace(" ", ""),
                            if (affinityGroup.isEmpty) "individual" else affinityGroup
                          )
                      )
                    )
                  )
                )
              case _ => throw new IllegalArgumentException(s"Invalid line: $line")
            }
          }

          val table = emptyTable.copy(rows = rows)

          Future.successful(Ok(pdf_recovery(table)))
        }.getOrElse(Future.successful(Ok(pdf_recovery(emptyTable))))
      } else {
        Future.successful(Ok(pdf_recovery(emptyTable)))
      }
    }
}
