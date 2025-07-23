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
package controllers

import cats.implicits.{ catsSyntaxApplicativeId, toTraverseOps }
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.FileIO
import org.slf4j.{ Logger, LoggerFactory }
import play.api.data.Form
import play.api.data.Forms.{ mapping, nonEmptyText, text }
import play.api.i18n.I18nSupport
import play.api.libs.Files.DefaultTemporaryFileCreator
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ Action, AnyContent, MessagesControllerComponents, Result }
import uk.gov.hmrc.eeittadminfrontend.connectors.GformConnector
import uk.gov.hmrc.eeittadminfrontend.models.fileupload.{ EnvelopeId, EnvelopeIdForm }
import uk.gov.hmrc.eeittadminfrontend.models.logging.CustomerDataAccessLog
import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse }
import uk.gov.hmrc.internalauth.client.{ AuthenticatedRequest, FrontendAuthComponents, Retrieval }

import java.io.FileOutputStream
import java.nio.file.{ Files, Path }
import java.util.zip.{ ZipEntry, ZipOutputStream }
import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

case class AccessEnvelopeForm(accessReason: String, envelopeId: String)
case class AccessEnvelopesForm(accessReason: String, envelopeIds: String)

class EnvelopeController @Inject() (
  frontendAuthComponents: FrontendAuthComponents,
  gformConnector: GformConnector,
  messagesControllerComponents: MessagesControllerComponents,
  envelope_html: views.html.envelope,
  envelope_options: views.html.envelope_options,
  defaultTemporaryFileCreator: DefaultTemporaryFileCreator
)(implicit ec: ExecutionContext, materializer: Materializer)
    extends GformAdminFrontendController(frontendAuthComponents, messagesControllerComponents) with I18nSupport {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  private type AuthRequest = AuthenticatedRequest[AnyContent, Retrieval.Username]

  private val envelopeIdForm: Form[EnvelopeIdForm] = Form(
    mapping("envelopeId" -> mapping("value" -> text)(EnvelopeId.apply)(EnvelopeId.unapply))(EnvelopeIdForm.apply)(
      EnvelopeIdForm.unapply
    )
  )

  private val accessEnvelopeForm: Form[AccessEnvelopeForm] = Form(
    mapping(
      "accessReason" -> nonEmptyText,
      "envelopeId"   -> nonEmptyText
    )(AccessEnvelopeForm.apply)(AccessEnvelopeForm.unapply)
  )

  def envelope(reason: Option[String], envIds: Option[String], errorCode: Option[String]) =
    authorizedRead.async { implicit request =>
      Future.successful(Ok(envelope_html(envelopeIdForm, reason, envIds, errorCode)))
    }

  private def WithUserLogin(f: (EnvelopeId, String) => HeaderCarrier => Future[Result]) =
    authorizedRead.async { implicit request =>
      envelopeIdForm
        .bindFromRequest()
        .fold(
          formWithErrors => Future.successful(BadRequest(envelope_html(envelopeIdForm, None, None, None))),
          envelopeId => f(envelopeId.envelopeId, request.retrieval.value)(hc)
        )
    }

  def findEnvelope() =
    WithUserLogin { (envelopeId, userData) => implicit hc =>
      logger.info(s"User '$userData' queried for gform envelopeId $envelopeId")
      gformConnector.getEnvelopeById(envelopeId).map {
        case Right(_)    => Redirect(routes.EnvelopeController.envelopeOptions(envelopeId))
        case Left(error) => BadRequest(error)
      }
    }

  def envelopeOptions(envelopeId: EnvelopeId, reason: Option[String]) =
    authorizedRead.async { implicit request =>
      Future.successful(Ok(envelope_options(envelopeId, reason)))
    }

  def showEnvelope(envelopeId: EnvelopeId): Action[AnyContent] =
    authorizedRead.async { implicit request =>
      showJsonResult(
        gformConnector.getEnvelopeById(envelopeId),
        Left(s"User '$username' viewed gform envelopeId '$envelopeId'")
      )
    }

  def showRetrievals(): Action[AnyContent] =
    handleCommonAuthAndBind { accessEnvelope => implicit request =>
      showJsonResult(
        gformConnector.getRetrievalsForEnvelopeId(EnvelopeId(accessEnvelope.envelopeId)),
        Right(CustomerDataAccessLog(username, "viewed authenticated user retrievals", accessEnvelope))
      )
    }

  def showFormData(): Action[AnyContent] =
    handleCommonAuthAndBind { accessEnvelope => implicit request =>
      showJsonResult(
        gformConnector.getFormDataForEnvelopeId(EnvelopeId(accessEnvelope.envelopeId)),
        Right(CustomerDataAccessLog(username, "viewed form data", accessEnvelope))
      )
    }

  def downloadEnvelope(): Action[AnyContent] =
    handleCommonAuthAndBind { accessEnvelope => implicit request =>
      gformConnector
        .downloadEnvelope(EnvelopeId(accessEnvelope.envelopeId))
        .map { response =>
          if (response.status == 200) {
            logSensitiveDataAccess(CustomerDataAccessLog(username, "downloaded DMS envelope", accessEnvelope))
            Ok.streamed(response.bodyAsSource, None)
              .withHeaders(
                CONTENT_DISPOSITION -> s"""attachment; filename = "${accessEnvelope.envelopeId}.zip""""
              )
          } else {
            redirect(accessEnvelope)
          }
        }
    }

  def downloadDataStore(): Action[AnyContent] =
    handleCommonAuthAndBind { accessEnvelope => implicit request =>
      gformConnector
        .downloadDataStore(EnvelopeId(accessEnvelope.envelopeId))
        .map { response =>
          if (response.status == 200) {
            logSensitiveDataAccess(CustomerDataAccessLog(username, "downloaded DataStore JSON file", accessEnvelope))
            Ok.streamed(response.bodyAsSource, None)
              .withHeaders(
                CONTENT_DISPOSITION -> s"""attachment; filename = "${accessEnvelope.envelopeId}.json""""
              )
          } else {
            redirect(accessEnvelope)
          }
        }
    }

  private def username(implicit request: AuthenticatedRequest[AnyContent, Retrieval.Username]): String =
    request.retrieval.value

  private def redirect(accessEnvelope: AccessEnvelopeForm): Result =
    Redirect(
      routes.EnvelopeController
        .envelopeOptions(EnvelopeId(accessEnvelope.envelopeId), Some(accessEnvelope.accessReason))
    )

  private def handleCommonAuthAndBind(f: AccessEnvelopeForm => AuthRequest => Future[Result]): Action[AnyContent] =
    authorizedRead.async { implicit request =>
      accessEnvelopeForm
        .bindFromRequest()
        .fold(
          formWithErrors =>
            Future.successful {
              formWithErrors.data.get("envelopeId").fold(BadRequest(envelope_html(envelopeIdForm, None, None, None))) {
                envelopeId =>
                  BadRequest(envelope_options(EnvelopeId(envelopeId), formWithErrors.data.get("accessReason")))
              }
            },
          accessEnvelope => f(accessEnvelope)(request)
        )
    }

  private def showJsonResult(
    f: Future[Either[String, JsValue]],
    toLog: Either[String, CustomerDataAccessLog]
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Result] =
    f.map {
      case Right(payload) =>
        toLog match {
          case Right(sd) => logSensitiveDataAccess(sd)
          case Left(msg) => logger.info(msg)
        }
        Ok(Json.prettyPrint(payload))
      case Left(error) => BadRequest(error)
    }

  private val accessEnvelopesForm: Form[AccessEnvelopesForm] = Form(
    mapping(
      "accessReason" -> nonEmptyText,
      "envelopeIds"  -> nonEmptyText
    )(AccessEnvelopesForm.apply)(AccessEnvelopesForm.unapply)
  )

  def downloadMultipleEnvelopes() = {
    def getEnvelopeIds(envelopeIds: String): List[EnvelopeId] =
      envelopeIds.split(",").map(_.trim).filterNot(_.isEmpty).toSet.toList.map(EnvelopeId(_))

    authorizedRead.async { implicit request =>
      accessEnvelopesForm
        .bindFromRequest()
        .fold(
          formWithErrors =>
            BadRequest(
              envelope_html(envelopeIdForm, formWithErrors.data.get("accessReason"), None, None)
            ).pure[Future],
          accessEnvelopes => {
            val envelopeIds: List[EnvelopeId] = getEnvelopeIds(accessEnvelopes.envelopeIds)
            if (envelopeIds.size > 50) {
              Redirect(
                routes.EnvelopeController
                  .envelope(Some(accessEnvelopes.accessReason), Some(accessEnvelopes.envelopeIds), Some("TOO_MANY"))
              ).pure[Future]
            } else {
              val envelopeZipFiles = retrieveAndSaveTemp(envelopeIds, ".zip", gformConnector.downloadEnvelope)
              val dataStoreJsonFiles = retrieveAndSaveTemp(envelopeIds, ".json", gformConnector.downloadDataStore)

              for {
                dmsEnvelopes   <- envelopeZipFiles
                dataStoreJsons <- dataStoreJsonFiles
              } yield {
                val combinedFiles = dmsEnvelopes ++ dataStoreJsons

                if (combinedFiles.nonEmpty) {
                  logSensitiveDataAccess(
                    CustomerDataAccessLog(
                      username,
                      "downloaded ALL files",
                      accessEnvelopes.accessReason,
                      envelopeIds.map(_.value)
                    )
                  )

                  Ok.streamed(FileIO.fromPath(compressToFile(combinedFiles)), None)
                    .withHeaders(
                      CONTENT_DISPOSITION -> s"""attachment; filename = "envelopes.zip""""
                    )
                } else {
                  Redirect(
                    routes.EnvelopeController
                      .envelope(
                        Some(accessEnvelopes.accessReason),
                        Some(accessEnvelopes.envelopeIds),
                        Some("NO_RESULT")
                      )
                  )
                }
              }
            }
          }
        )
    }
  }

  private def retrieveAndSaveTemp(
    envelopeIds: List[EnvelopeId],
    ext: String,
    download: EnvelopeId => Future[HttpResponse]
  ): Future[List[(String, Path)]] =
    envelopeIds
      .traverse { envelopeId =>
        download(envelopeId)
          .flatMap { response =>
            if (response.status == 200) {
              val tempFile = defaultTemporaryFileCreator.create()
              response.bodyAsSource
                .runWith(FileIO.toPath(tempFile.path))
                .map(_ => Some(s"${envelopeId.value}/${envelopeId.value}$ext" -> tempFile.path))
            } else {
              Option.empty[(String, Path)].pure[Future]
            }
          }
      }
      .map(_.filter(_.isDefined).map(_.get))

  private def compressToFile(files: List[(String, Path)]): Path = {
    val zipFile = defaultTemporaryFileCreator.create()
    val zos = new ZipOutputStream(new FileOutputStream(zipFile))

    try for ((name, path) <- files) {
      zos.putNextEntry(new ZipEntry(name))
      zos.write(Files.readAllBytes(path))
      zos.closeEntry()
    } finally zos.close()

    zipFile
  }

  private def logSensitiveDataAccess(
    accessLog: CustomerDataAccessLog
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Unit = {
    logger.warn(accessLog.getMessage)
    gformConnector
      .saveLog(accessLog)
      .map {
        case Right(_)    => ()
        case Left(error) => logger.error(s"Unable to persist $accessLog with reason '$error'")
      }
    ()
  }
}
