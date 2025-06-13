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
import play.api.Configuration
import play.api.data.Form
import play.api.data.Forms.{ mapping, nonEmptyText, text }
import play.api.i18n.I18nSupport
import play.api.libs.Files.DefaultTemporaryFileCreator
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ Action, AnyContent, MessagesControllerComponents, Result }
import uk.gov.hmrc.eeittadminfrontend.connectors.GformConnector
import uk.gov.hmrc.eeittadminfrontend.models.fileupload.{ EnvelopeId, EnvelopeIdForm }
import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse }
import uk.gov.hmrc.internalauth.client.{ AuthenticatedRequest, FrontendAuthComponents, Retrieval }

import java.io.File
import java.nio.file.Path
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
  defaultTemporaryFileCreator: DefaultTemporaryFileCreator,
  conf: Configuration
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

  def envelope() =
    authorizedRead.async { implicit request =>
      Future.successful(Ok(envelope_html(envelopeIdForm)))
    }

  private def WithUserLogin(f: (EnvelopeId, String) => HeaderCarrier => Future[Result]) =
    authorizedRead.async { implicit request =>
      envelopeIdForm
        .bindFromRequest()
        .fold(
          formWithErrors => Future.successful(BadRequest(envelope_html(envelopeIdForm))),
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
        s"User '$username' viewed gform envelopeId '$envelopeId'"
      )
    }

  def showRetrievals(): Action[AnyContent] =
    handleCommonAuthAndBind { accessEnvelope => implicit request =>
      showJsonResult(
        gformConnector.getRetrievalsForEnvelopeId(EnvelopeId(accessEnvelope.envelopeId)),
        s"Sensitive data access: User '$username', reason '${accessEnvelope.accessReason}', viewed authenticated user retrievals for gform envelopeId '${accessEnvelope.envelopeId}'"
      )
    }

  def showFormData(): Action[AnyContent] =
    handleCommonAuthAndBind { accessEnvelope => implicit request =>
      showJsonResult(
        gformConnector.getFormDataForEnvelopeId(EnvelopeId(accessEnvelope.envelopeId)),
        s"Sensitive data access: User '$username', reason '${accessEnvelope.accessReason}', viewed form data for gform envelopeId '${accessEnvelope.envelopeId}'"
      )
    }

  def downloadEnvelope(): Action[AnyContent] =
    handleCommonAuthAndBind { accessEnvelope => implicit request =>
      gformConnector
        .downloadEnvelope(EnvelopeId(accessEnvelope.envelopeId))
        .map { response =>
          if (response.status == 200) {
            logger.info(
              s"Sensitive data access: User '$username', reason '${accessEnvelope.accessReason}', downloaded DMS envelope gform envelopeId '${accessEnvelope.envelopeId}'"
            )
            Ok.streamed(response.bodyAsSource, None)
              .withHeaders(
                CONTENT_DISPOSITION -> s"""attachment; filename = "${accessEnvelope.envelopeId}.zip""""
              )
          } else {
            BadRequest(envelope_options(EnvelopeId(accessEnvelope.envelopeId), Some(accessEnvelope.accessReason)))
          }
        }
    }

  def downloadDataStore(): Action[AnyContent] =
    handleCommonAuthAndBind { accessEnvelope => implicit request =>
      gformConnector
        .downloadDataStore(EnvelopeId(accessEnvelope.envelopeId))
        .map { response =>
          if (response.status == 200) {
            logger.info(
              s"Sensitive data access: User '$username', reason '${accessEnvelope.accessReason}', downloaded DataStore JSON for gform envelopeId '${accessEnvelope.envelopeId}'"
            )
            Ok.streamed(response.bodyAsSource, None)
              .withHeaders(
                CONTENT_DISPOSITION -> s"""attachment; filename = "${accessEnvelope.envelopeId}.json""""
              )
          } else {
            badRequest(accessEnvelope)
          }
        }
    }

  private def username(implicit request: AuthenticatedRequest[AnyContent, Retrieval.Username]): String =
    request.retrieval.value

  private def badRequest(accessEnvelope: AccessEnvelopeForm)(implicit request: AuthRequest): Result =
    BadRequest(envelope_options(EnvelopeId(accessEnvelope.envelopeId), Some(accessEnvelope.accessReason)))

  private def handleCommonAuthAndBind(f: AccessEnvelopeForm => AuthRequest => Future[Result]): Action[AnyContent] =
    authorizedRead.async { implicit request =>
      accessEnvelopeForm
        .bindFromRequest()
        .fold(
          formWithErrors =>
            Future.successful {
              formWithErrors.data.get("envelopeId").fold(BadRequest(envelope_html(envelopeIdForm))) { envelopeId =>
                BadRequest(envelope_options(EnvelopeId(envelopeId), formWithErrors.data.get("accessReason")))
              }
            },
          accessEnvelope => f(accessEnvelope)(request)
        )
    }

  private def showJsonResult(f: Future[Either[String, JsValue]], message: String): Future[Result] =
    f.map {
      case Right(payload) =>
        logger.info(message)
        Ok(Json.prettyPrint(payload))
      case Left(error) => BadRequest(error)
    }

  private val accessEnvelopesForm: Form[AccessEnvelopesForm] = Form(
    mapping(
      "accessReason" -> nonEmptyText,
      "envelopeIds"  -> nonEmptyText
    )(AccessEnvelopesForm.apply)(AccessEnvelopesForm.unapply)
  )

  def downloadMultipleEnvelopes() =
    authorizedRead.async { implicit request =>
      accessEnvelopesForm
        .bindFromRequest()
        .fold(
          formWithErrors =>
            Future.successful {
              println(s"CM: formWithErrors: $formWithErrors")
              formWithErrors.data.get("envelopeId").fold(BadRequest(envelope_html(envelopeIdForm))) { envelopeId =>
                BadRequest(envelope_options(EnvelopeId(envelopeId), formWithErrors.data.get("accessReason")))
              }
            },
          accessEnvelopes => {
            val envelopeIds = accessEnvelopes.envelopeIds.split(",").map(value => EnvelopeId(value.trim())).toList
            val envelopeZipFiles = getAndCreateTemps(envelopeIds, ".zip", gformConnector.downloadEnvelope)
            val dataStoreJsonFiles = getAndCreateTemps(envelopeIds, ".json", gformConnector.downloadDataStore)

            for {
              dmsEnvelopes   <- envelopeZipFiles
              dataStoreJsons <- dataStoreJsonFiles
            } yield {
              val allFiles = dmsEnvelopes ++ dataStoreJsons

              if (allFiles.nonEmpty) {
                // TODO need logging and look at other files - infoArchive?
                val tempZipFile = defaultTemporaryFileCreator.create()
                zip(tempZipFile, allFiles)
                Ok.streamed(FileIO.fromPath(tempZipFile), None)
                  .withHeaders(
                    CONTENT_TYPE        -> "application/zip",
                    CONTENT_DISPOSITION -> s"""attachment; filename = "envelopes.zip""""
                  )
              } else {
                BadRequest("No results")
              }
            }
          }
        )
    }

  private def getAndCreateTemps(envelopeIds: List[EnvelopeId], ext: String, f: EnvelopeId => Future[HttpResponse]) =
    envelopeIds
      .traverse { envelopeId =>
        f(envelopeId)
          .flatMap { response =>
            if (response.status == 200) {
              val tempFile = defaultTemporaryFileCreator.create()
              response.bodyAsSource
                .runWith(FileIO.toPath(tempFile.path))
                .map(_ => Some(s"${envelopeId.value}$ext" -> tempFile.path))
            } else {
              Option.empty[(String, Path)].pure[Future]
            }
          }
      }
      .map(_.filter(_.isDefined).map(_.get))

  private def zip(out: File, files: List[(String, Path)]) = {
    import java.io.{ BufferedInputStream, FileInputStream, FileOutputStream }
    import java.util.zip.{ ZipEntry, ZipOutputStream }

    val zip = new ZipOutputStream(new FileOutputStream(out))

    files.foreach { case (name, path) =>
      zip.putNextEntry(new ZipEntry(name))
      val in = new BufferedInputStream(new FileInputStream(path.toAbsolutePath.toString))
      var b = in.read()
      while (b > -1) {
        zip.write(b)
        b = in.read()
      }
      in.close()
      zip.closeEntry()
    }
    zip.close()
  }
}
