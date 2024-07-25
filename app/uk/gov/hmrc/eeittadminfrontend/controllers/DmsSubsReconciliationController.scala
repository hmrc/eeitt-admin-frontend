/*
 * Copyright 2024 HM Revenue & Customs
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

import play.api.i18n.I18nSupport
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.Json
import play.api.mvc.{ Action, AnyContent, MessagesControllerComponents, MultipartFormData }
import uk.gov.hmrc.eeittadminfrontend.connectors.SubmissionConsolidatorConnector
import uk.gov.hmrc.eeittadminfrontend.models.DmsReport
import uk.gov.hmrc.eeittadminfrontend.models.sdes.SdesReportsPageData
import uk.gov.hmrc.eeittadminfrontend.services.DmsSubReconciliationService
import uk.gov.hmrc.eeittadminfrontend.utils.FileUtil
import uk.gov.hmrc.eeittadminfrontend.views.html.dmsSubsReconciliation
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }
import scala.io.Source

class DmsSubsReconciliationController @Inject() (
  frontendAuthComponents: FrontendAuthComponents,
  messagesControllerComponents: MessagesControllerComponents,
  reconciliationService: DmsSubReconciliationService,
  subConConnector: SubmissionConsolidatorConnector,
  dmsSubsUploadCsv: dmsSubsReconciliation.dms_subs_upload_csv,
  dmsSubsReconciliationReport: dmsSubsReconciliation.dms_subs_reconciliation_report
) extends GformAdminFrontendController(frontendAuthComponents, messagesControllerComponents) with I18nSupport {

  implicit val ec: ExecutionContext = messagesControllerComponents.executionContext
  def dmsSubmissionsUploadCsv(): Action[AnyContent] =
    authorizedRead.async { implicit request =>
      Future.successful(Ok(dmsSubsUploadCsv()))
    }

  def uploadDmsSubmissionsCsv(): Action[MultipartFormData[TemporaryFile]] =
    authorizedWrite.async(parse.multipartFormData) { implicit request =>
      val file = TemporaryFile.temporaryFileToFile(request.body.file("file").get.ref)

      subConConnector.getSdesSubmissions(0, 200, None, None, None).map {
        case sdesReport @ SdesReportsPageData(_, count) =>
          val reconcileData = reconciliationService.sdesToBeReconciled(sdesReport, DmsReport(file))
          file.delete()

          if (reconcileData.nonEmpty) {
            val reconcileFile = FileUtil
              .createFile(
                "reconcileData.json",
                Json
                  .toJson(
                    SdesReportsPageData(
                      reconcileData,
                      reconcileData.length.toLong
                    )
                  )
                  .toString()
              )
              .getAbsolutePath

            Redirect(routes.DmsSubsReconciliationController.dmsSubmissionsUploadCsv())
              .flashing(
                "success"  -> s"""File uploaded successfully!!! ${reconcileData.length} SDES submissions of $count marked as unprocessed have been received by DMS and will be marked as FileProcessedManualConfirmed""",
                "filePath" -> reconcileFile
              )
          } else {
            Redirect(routes.DmsSubsReconciliationController.dmsSubmissionsUploadCsv())
              .flashing("error" -> "No SDES submissions marked as unprocessed have been received by DMS")
          }
        case _ =>
          file.delete()
          Redirect(routes.DmsSubsReconciliationController.dmsSubmissionsUploadCsv())
            .flashing("error" -> "Error while getting SDES reports data")
      }
    }

  def reconcileDmsSubmissions(filePath: String): Action[AnyContent] = authorizedRead.async { implicit request =>
    val uploadData = Source.fromFile(filePath)
    val reconcileData = Json.parse(uploadData.mkString).as[SdesReportsPageData]
    uploadData.close()

    reconciliationService.sdesReconcile(reconcileData.sdesSubmissions).map { _ =>
      Ok(dmsSubsReconciliationReport(reconcileData))
    }
  }
}
