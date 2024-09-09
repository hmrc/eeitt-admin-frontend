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

import play.api.data.Forms.{ optional, text }
import play.api.data.{ Form, Forms }
import play.api.i18n.I18nSupport
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.Json
import play.api.mvc.{ Action, AnyContent, MessagesControllerComponents, MultipartFormData }
import uk.gov.hmrc.eeittadminfrontend.connectors.GformConnector
import uk.gov.hmrc.eeittadminfrontend.history.DateFilter
import uk.gov.hmrc.eeittadminfrontend.models.DmsReport
import uk.gov.hmrc.eeittadminfrontend.models.sdes.{ SdesDestination, SdesFilter, SdesReconciliation, SdesSubmissionPageData }
import uk.gov.hmrc.eeittadminfrontend.services.DmsSubReconciliationService
import uk.gov.hmrc.eeittadminfrontend.views.html.dmsSubsReconciliation
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents

import java.time.{ LocalDateTime, ZoneId }
import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class DmsSubsReconciliationController @Inject() (
  frontendAuthComponents: FrontendAuthComponents,
  messagesControllerComponents: MessagesControllerComponents,
  reconciliationService: DmsSubReconciliationService,
  gformConnector: GformConnector,
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
      if (request.body.file("file").isDefined) {
        val file = TemporaryFile.temporaryFileToFile(request.body.file("file").get.ref)

        val sdesFilter = SdesFilter(
          0,
          200,
          Some(false),
          None,
          None,
          Some(SdesDestination.Dms),
          Some(DateFilter.DateTime(LocalDateTime.now(ZoneId.of("UTC")).minusDays(7))),
          None
        )

        gformConnector.getSdesSubmissions(sdesFilter).map {
          case sdesReport @ SdesSubmissionPageData(_, count) =>
            val reconcileData = reconciliationService.sdesToBeReconciled(sdesReport, DmsReport(file))
            file.delete()

            val notificationMessage =
              s"${reconcileData.length} submissions of $count not processed have been received by DMS and will be marked as FileProcessedManualConfirmed, from ${sdesFilter.from.get}"
            Ok(
              dmsSubsUploadCsv(
                notificationMessage,
                Some(SdesReconciliation(reconcileData, reconcileData.length.toLong))
              )
            )
          case _ =>
            file.delete()
            Redirect(routes.DmsSubsReconciliationController.dmsSubmissionsUploadCsv())
              .flashing("error" -> "Error while getting SDES submissions data.")
        }
      } else {
        Future.successful(
          Redirect(routes.DmsSubsReconciliationController.dmsSubmissionsUploadCsv())
            .flashing("error" -> "File upload error.")
        )
      }
    }

  def reconcileDmsSubmissions(): Action[AnyContent] = authorizedRead.async { implicit request =>
    form
      .bindFromRequest()
      .fold(
        _ => Future.successful(BadRequest(dmsSubsUploadCsv())),
        optionalData =>
          optionalData
            .map { data: String =>
              val reconcileData = Json.fromJson[SdesReconciliation](Json.parse(data)).get
              reconciliationService.sdesReconcile(reconcileData.sdesSubmissions).map { _ =>
                Ok(dmsSubsReconciliationReport(reconcileData))
              }
            }
            .getOrElse {
              Future.successful(
                Redirect(routes.DmsSubsReconciliationController.dmsSubmissionsUploadCsv())
                  .flashing("error" -> "Setting submission state to file manually processed was unsuccessful.")
              )
            }
      )
  }

  private val form: Form[Option[String]] = play.api.data.Form(
    Forms.single(
      "reconcileData" -> optional(text)
    )
  )
}
