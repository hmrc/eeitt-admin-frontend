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

package uk.gov.hmrc.eeittadminfrontend.controllers

import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{ FileIO, Framing, Keep, Sink, StreamConverters }
import org.apache.pekko.util.ByteString
import cats.syntax.all._

import javax.inject.Inject
import julienrf.json.derived
import org.slf4j.{ Logger, LoggerFactory }
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.I18nSupport
import play.api.libs.Files.TemporaryFile.temporaryFileToPath
import play.api.libs.json._
import play.api.mvc.{ AnyContent, MessagesControllerComponents, Request }
import play.twirl.api.Html
import uk.gov.hmrc.eeittadminfrontend.connectors.GformConnector
import uk.gov.hmrc.eeittadminfrontend.models._
import uk.gov.hmrc.eeittadminfrontend.services.{ BatchUploadService, GformService }
import uk.gov.hmrc.eeittadminfrontend.utils.DateUtils
import uk.gov.hmrc.eeittadminfrontend.validators.FormTemplateValidator
import uk.gov.hmrc.internalauth.client.{ AuthenticatedRequest, FrontendAuthComponents, Retrieval }
import uk.gov.hmrc.govukfrontend.views.viewmodels.errormessage.ErrorMessage
import uk.gov.hmrc.govukfrontend.views.html.components
import uk.gov.hmrc.govukfrontend.views.viewmodels.content
import uk.gov.hmrc.govukfrontend.views.viewmodels.errorsummary.{ ErrorLink, ErrorSummary }
import uk.gov.hmrc.govukfrontend.views.Aliases.Text
import play.api.data.Forms.text
import play.api.data.Forms
import uk.gov.hmrc.eeittadminfrontend.deployment.GithubPath

import java.io.{ BufferedOutputStream, ByteArrayInputStream, ByteArrayOutputStream }
import java.time.Instant
import java.util.zip.{ ZipEntry, ZipOutputStream }
import scala.concurrent.{ ExecutionContext, Future }

sealed trait RefreshTemplateResult extends Product with Serializable
case class RefreshSuccesful(formTemplateId: FormTemplateId) extends RefreshTemplateResult
case class RefreshError(formTemplateId: FormTemplateId, errorMessage: String) extends RefreshTemplateResult

object RefreshTemplateResult {
  implicit val format: OFormat[RefreshTemplateResult] = derived.oformat()
}

sealed trait RefreshResult extends Product with Serializable

object RefreshResult {
  implicit val format: OFormat[RefreshResult] = derived.oformat()
}

case class RefreshTemplateResults(results: List[RefreshTemplateResult]) extends RefreshResult {
  def addResult(rtr: RefreshTemplateResult) = RefreshTemplateResults(rtr :: results)
}
object RefreshTemplateResults {
  val empty = RefreshTemplateResults(List.empty[RefreshTemplateResult])
}
case object NoTempatesToRefresh extends RefreshResult

class GformsController @Inject() (
  frontendAuthComponents: FrontendAuthComponents,
  gformConnector: GformConnector,
  gformService: GformService,
  formTemplateValidator: FormTemplateValidator,
  batchUploadService: BatchUploadService,
  messagesControllerComponents: MessagesControllerComponents,
  gform_formtemplate_pii: uk.gov.hmrc.eeittadminfrontend.views.html.gform_formtemplate_pii,
  gform_formtemplates_pii: uk.gov.hmrc.eeittadminfrontend.views.html.gform_formtemplates_pii,
  gform_formtemplates_pii_home: uk.gov.hmrc.eeittadminfrontend.views.html.gform_formtemplates_pii_home,
  gform_page: uk.gov.hmrc.eeittadminfrontend.views.html.gform_page,
  batch_upload: uk.gov.hmrc.eeittadminfrontend.views.html.batch_upload,
  handlebars_template: uk.gov.hmrc.eeittadminfrontend.views.html.handlebars_template,
  confirmation: uk.gov.hmrc.eeittadminfrontend.views.html.confirmation
)(implicit ec: ExecutionContext, m: Materializer)
    extends GformAdminFrontendController(frontendAuthComponents, messagesControllerComponents) with I18nSupport {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  private def fileByteData(
    fileListFormTemplates: Seq[(FormTemplateId, JsValue)],
    fileListHandlebars: Seq[(FormTemplateId, String)],
    fileListHandlebarsSchemas: Seq[(FormTemplateId, JsValue)]
  ): ByteArrayInputStream = {

    val baos = new ByteArrayOutputStream()
    val zos = new ZipOutputStream(new BufferedOutputStream(baos))

    try {
      fileListFormTemplates.foreach { case (formTemplateId, formTemplate) =>
        zos.putNextEntry(new ZipEntry(GithubPath.RootPath.zipPath + formTemplateId.value + ".json"))
        zos.write(Json.prettyPrint(formTemplate).getBytes())
        zos.closeEntry()
      }

      fileListHandlebars.foreach { case (formTemplateId, formTemplate) =>
        zos.putNextEntry(new ZipEntry(GithubPath.HandlebarsPath.zipPath + formTemplateId.value + ".hbs"))
        zos.write(formTemplate.getBytes())
        zos.closeEntry()
      }

      fileListHandlebarsSchemas.foreach { case (formTemplateId, formTemplate) =>
        zos.putNextEntry(new ZipEntry(GithubPath.HandlebarsSchemaPath.zipPath + formTemplateId.value + ".json"))
        zos.write(Json.prettyPrint(formTemplate).getBytes())
        zos.closeEntry()
      }
    } finally zos.close()

    new ByteArrayInputStream(baos.toByteArray)
  }

  def getBlob =
    authorizedRead.async { implicit request =>
      val username = request.retrieval.value
      logger.info(s" $username ask for all templates as a zip blob")
      val blobTemplates: Future[Seq[(FormTemplateId, JsValue)]] =
        gformConnector.getAllGformsTemplates.flatMap {
          case JsArray(templates) =>
            val formTemplateIds = templates.collect {
              case JsString(template) if !template.startsWith("specimen-") => FormTemplateId(template)
            }
            Future.traverse(formTemplateIds.toSeq) { formTemplateId =>
              gformConnector
                .getGformsTemplate(formTemplateId)
                .map {
                  case Right(formTemplate) => (formTemplateId, formTemplate)
                  case Left(error)         => (formTemplateId, JsString(error))
                }
            }
          case _ => Future.successful(Seq.empty)
        }
      val blobHandlebars: Future[Seq[(FormTemplateId, String)]] =
        gformConnector.getAllHandlebarsTemplates.flatMap {
          case templates: JsArray =>
            val formTemplateIds = templates.value.collect { case JsString(template) =>
              FormTemplateId(template)
            }
            Future.traverse(formTemplateIds.toSeq) { formTemplateId =>
              gformConnector
                .getRawHandlebarsTemplate(formTemplateId)
                .map {
                  case Right(formTemplate) => (formTemplateId, formTemplate)
                  case Left(error)         => (formTemplateId, error)
                }
            }
          case _ => Future.successful(Seq.empty)
        }
      val blobHandlebarsSchemas: Future[Seq[(FormTemplateId, JsValue)]] =
        gformConnector.getAllHandlebarsSchemas.flatMap {
          case templates: JsArray =>
            val formTemplateIds = templates.value.collect { case JsString(template) =>
              FormTemplateId(template)
            }
            Future.traverse(formTemplateIds.toSeq) { formTemplateId =>
              gformConnector
                .getHandlebarsSchema(formTemplateId)
                .map(_.map(_.schema))
                .map {
                  case Right(formTemplate) => (formTemplateId, formTemplate)
                  case Left(error)         => (formTemplateId, JsString(error))
                }
            }
          case _ => Future.successful(Seq.empty)
        }
      for {
        handlebarsSchemas <- blobHandlebarsSchemas
        handlebars        <- blobHandlebars
        templates         <- blobTemplates
      } yield {
        val now = Instant.now()
        Ok.chunked(
          StreamConverters.fromInputStream(() => fileByteData(templates, handlebars, handlebarsSchemas))
        ).withHeaders(
          CONTENT_TYPE -> "application/zip",
          CONTENT_DISPOSITION ->
            s"""attachment; filename = "gform-prod-blob-${DateUtils.formatInstantNoSpace(now)}.zip""""
        )
      }
    }

  def getGformByFormType =
    authorizedRead.async { implicit request =>
      val username = request.retrieval.value
      gFormForm
        .bindFromRequest()
        .fold(
          formWithErrors => Future.successful(BadRequest(gform_page(gFormForm))),
          gformIdAndVersion => {
            logger.info(s"$username Queried for ${gformIdAndVersion.formTemplateId}")
            gformConnector.getGformsTemplate(gformIdAndVersion.formTemplateId).map {
              case Left(ex) =>
                Ok(s"Problem when fetching form template: ${gformIdAndVersion.formTemplateId}. Reason: $ex")
              case Right(r) => Ok(Json.prettyPrint(r))
            }
          }
        )
    }

  def getAllTemplates =
    authorizedRead.async { implicit request =>
      val username = request.retrieval.value
      logger.info(s"$username Queried for all form templates")
      for {
        templates        <- gformConnector.getAllGformsTemplates
        handlebars       <- gformConnector.getAllHandlebarsTemplates
        handlebarsSchema <- gformConnector.getAllHandlebarsSchemas
      } yield {
        val mergedJson = Json.obj(
          "formTemplates"       -> Json.toJsFieldJsValueWrapper(templates),
          "handlebarsTemplates" -> Json.toJsFieldJsValueWrapper(handlebars),
          "handlebarsSchemas"   -> Json.toJsFieldJsValueWrapper(handlebarsSchema)
        )
        Ok(mergedJson)
      }
    }

  def reloadTemplates =
    authorizedWrite.async { implicit request =>
      val username = request.retrieval.value
      logger.info(s"$username Reload all form templates")

      for {
        maybeHandlebarsSchemaIds   <- gformConnector.getAllHandlebarsSchemas.map(_.as[List[FormTemplateId]])
        maybeHandlebarsTemplateIds <- gformConnector.getAllHandlebarsTemplates.map(_.as[List[FormTemplateId]])
        maybeTemplateIds           <- gformConnector.getAllGformsTemplates.map(_.as[List[FormTemplateId]])
        result <- fetchAndSave(
                    maybeTemplateIds.filterNot(_.value.startsWith("specimen-")),
                    maybeHandlebarsTemplateIds,
                    maybeHandlebarsSchemaIds
                  )
      } yield Ok(Json.toJson(result))
    }

  def fetchAndSave(
    formTemplateIds: List[FormTemplateId],
    maybeHandlebarsTemplateIds: List[FormTemplateId],
    maybeHandlebarsSchemaIds: List[FormTemplateId]
  )(implicit request: AuthenticatedRequest[AnyContent, Retrieval.Username]): Future[RefreshResult] = {
    val username = request.retrieval.value

    val templatesResult: Future[RefreshTemplateResults] =
      formTemplateIds.foldLeft(Future.successful(RefreshTemplateResults.empty)) { case (resultsAcc, formTemplateId) =>
        logger.info(s"$username Refreshing formTemplateId: $formTemplateId")
        for {
          results         <- resultsAcc
          templateOrError <- gformConnector.getGformsTemplate(formTemplateId)
          resultTemplates <- templateOrError match {
                               case Left(error)     => Future.successful(Left(error))
                               case Right(template) => gformConnector.saveTemplate(formTemplateId, template)
                             }
          _ = logger.info(s"$username Refreshing formTemplateId: $formTemplateId finished: $resultTemplates")
          result <- processResult(formTemplateId, results, resultTemplates)
        } yield result
      }

    val handlebarsResult: Future[RefreshTemplateResults] =
      maybeHandlebarsTemplateIds.foldLeft(Future.successful(RefreshTemplateResults.empty)) {
        case (resultsAcc, formTemplateId) =>
          logger.info(s"$username Refreshing formTemplateId: $formTemplateId")
          for {
            results         <- resultsAcc
            templateOrError <- gformConnector.getRawHandlebarsTemplate(formTemplateId)
            resultTemplates <- templateOrError match {
                                 case Left(error)     => Future.successful(Left(error))
                                 case Right(template) => gformConnector.saveHandlebarsTemplate(formTemplateId, template)
                               }
            _ = logger.info(s"$username Refreshing formTemplateId: $formTemplateId finished: $resultTemplates")
            result <- processResult(formTemplateId, results, resultTemplates)
          } yield result
      }

    val handlebarsSchemaResult: Future[RefreshTemplateResults] =
      maybeHandlebarsSchemaIds.foldLeft(Future.successful(RefreshTemplateResults.empty)) {
        case (resultsAcc, formTemplateId) =>
          logger.info(s"$username Refreshing formTemplateId: $formTemplateId")
          for {
            results         <- resultsAcc
            templateOrError <- gformConnector.getHandlebarsSchema(formTemplateId).map(_.map(_.schema))
            resultTemplates <- templateOrError match {
                                 case Left(error)   => Future.successful(Left(error))
                                 case Right(schema) => gformConnector.saveHandlebarsSchema(formTemplateId, schema)
                               }
            _ = logger.info(s"$username Refreshing formTemplateId: $formTemplateId finished: $resultTemplates")
            result <- processResult(formTemplateId, results, resultTemplates)
          } yield result
      }

    for {
      templatesResults       <- templatesResult
      handlebarsResults      <- handlebarsResult
      handlebarsSchemaResult <- handlebarsSchemaResult
    } yield RefreshTemplateResults(
      templatesResults.results ++ handlebarsResults.results ++ handlebarsSchemaResult.results
    )
  }

  private def processResult(
    formTemplateId: FormTemplateId,
    results: RefreshTemplateResults,
    resultTemplates: Either[String, Unit]
  ): Future[RefreshTemplateResults] =
    resultTemplates match {
      case Left(error) => Future.successful(results.addResult(RefreshError(formTemplateId, error)))
      case Right(())   => Future.successful(results.addResult(RefreshSuccesful(formTemplateId)))
    }

  def getAllSchema =
    authorizedRead.async { implicit request =>
      val username = request.retrieval.value
      logger.info(s"$username Queried for all form Schema")
      gformConnector.getAllSchema.map(x => Ok(x))
    }

  def requestRemoval(formTemplateId: FormTemplateId) =
    authorizedDelete.async { implicit request =>
      val (pageError, fieldErrors) =
        request.flash.get("removeParamMissing").fold((NoErrors: HasErrors, Map.empty[String, ErrorMessage])) { _ =>
          (
            Errors(
              new components.GovukErrorSummary()(
                ErrorSummary(
                  errorList = List(
                    ErrorLink(
                      href = Some("#remove"),
                      content = content.Text(request.messages.messages("generic.error.selectOption"))
                    )
                  ),
                  title = content.Text(request.messages.messages("generic.error.selectOption.heading"))
                )
              )
            ),
            Map(
              "remove" -> ErrorMessage(
                content = Text(request.messages.messages("generic.error.selectOption"))
              )
            )
          )
        }
      Ok(confirmation(formTemplateId, pageError, fieldErrors)).pure[Future]
    }

  private val formRemoval: Form[String] = Form(
    Forms.single(
      "remove" -> Forms.nonEmptyText
    )
  )
  def confirmRemoval(formTemplateId: FormTemplateId) = authorizedDelete.async { implicit request =>
    formRemoval
      .bindFromRequest()
      .fold(
        _ =>
          Redirect(
            routes.GformsController.requestRemoval(formTemplateId)
          ).flashing("removeParamMissing" -> "true").pure[Future],
        {
          case "Yes" =>
            logger.info(s"${request.retrieval.value} deleted $formTemplateId ")
            gformConnector.deleteTemplate(formTemplateId).map { deleteResults =>
              Ok(Json.toJson(deleteResults))
            }
          case "No" =>
            Redirect(routes.GformsController.gformPage).pure[Future]
        }
      )
  }

  def deleteGformTemplate =
    authorizedDelete.async { implicit request =>
      val username = request.retrieval.value
      gFormForm
        .bindFromRequest()
        .fold(
          formWithErrors => Future.successful(BadRequest(gform_page(gFormForm))),
          gformId => {
            val formTemplateId = gformId.formTemplateId
            logger.info(s"$username queried for $formTemplateId to delete it")
            gformConnector.getGformsTemplate(formTemplateId).map {
              case Left(ex) =>
                Ok(s"Problem when fetching form template: $formTemplateId. Reason: $ex")
              case Right(r) => Redirect(routes.GformsController.requestRemoval(gformId.formTemplateId))
            }
          }
        )
    }

  def gformPage =
    authorizedRead.async { implicit request =>
      Future.successful(Ok(gform_page(gFormForm)))
    }

  private def getFilterList(filters: String) = filters.split(",").toList

  def gformFormTemplatesWithPIIInTitleHome() = authorizedRead.async { implicit request =>
    Future.successful(
      Ok(
        gform_formtemplates_pii_home(
          List.empty,
          Some("name,email,business,auth.")
        )
      )
    )
  }

  def gformFormTemplatesWithPIIInTitle = authorizedRead.async { implicit request =>
    gformFormTemplatesWithPIIInTitleForm
      .bindFromRequest()
      .fold(
        error => {
          logger.error("Failed to bind request to form for gformFormTemplatesWithPIIInTitle", error)
          Future.successful(Redirect(routes.GformsController.gformFormTemplatesWithPIIInTitleHome))
        },
        form =>
          (for {
            templateIds <- gformConnector.getAllGformsTemplates
                             .map(_.as[List[FormTemplateId]].filterNot(_.value.startsWith("specimen-")))
            formTemplatesWithPIIInTitle <-
              Future.traverse(templateIds) { formTemplateId =>
                gformConnector
                  .getTitlesWithPII(formTemplateId.formTemplateRawId, getFilterList(form.filters), false)
                  .map(p =>
                    FormTemplateWithPIIInTitle(
                      formTemplateId,
                      Some(p.piis.size)
                    )
                  )
                  .recover { case e =>
                    FormTemplateWithPIIInTitle(
                      formTemplateId,
                      None,
                      List(e.getMessage)
                    )
                  }
              }
          } yield Ok(
            gform_formtemplates_pii(
              formTemplatesWithPIIInTitle.sortBy(_.piiCount).reverse,
              form.filters
            )
          )).recover { case e =>
            InternalServerError("Failed to gformFormTemplatesWithPIIInTitle: " + e)
          }
      )
  }

  def gformFormTemplateWithPIIInTitleHome(
    formTemplateId: FormTemplateId,
    filters: String
  ) = authorizedRead.async { implicit request =>
    getFormTemplatePIIDetails(formTemplateId, filters).map { formTemplateWithPIIInTitleDetails =>
      Ok(
        gform_formtemplate_pii(
          formTemplateWithPIIInTitleDetails,
          formTemplateId,
          filters
        )
      )
    }

  }

  def gformFormTemplateWithPIIInTitle = authorizedRead.async { implicit request =>
    gformFormTemplateWithPIIInTitleForm
      .bindFromRequest()
      .fold(
        _ => Future.successful(Redirect(routes.GformsController.gformFormTemplatesWithPIIInTitleHome)),
        form =>
          getFormTemplatePIIDetails(form.formTemplateId, form.filters).map { formTemplateWithPIIInTitleDetails =>
            Ok(
              gform_formtemplate_pii(
                formTemplateWithPIIInTitleDetails,
                form.formTemplateId,
                form.filters
              )
            )
          }
      )
  }

  private def getFormTemplatePIIDetails(
    formTemplateId: FormTemplateId,
    filters: String
  )(implicit
    request: Request[AnyContent]
  ): Future[FormTemplateWithPIIInTitleDetails] =
    gformConnector
      .getTitlesWithPII(formTemplateId.formTemplateRawId, getFilterList(filters), true)
      .map(p =>
        FormTemplateWithPIIInTitleDetails(
          p.json.getOrElse("{}"),
          formTemplateId,
          p.piis
        )
      )

  def dbLookupFileUpload() =
    authorizedWrite.async(parse.multipartFormData) { implicit request =>
      val collectionName = request.body.dataParts("collectionName").head
      if (collectionName.isEmpty)
        Future.successful(BadRequest("'collectionName' param is empty"))
      else {
        logger.info(s"Uploading db lookup file for collection $collectionName")
        request.body
          .file("file")
          .map { filePart =>
            FileIO
              .fromPath(filePart.ref.getAbsoluteFile.toPath)
              .map(_.utf8String.replaceAll("\r\n", "\n"))
              .map(ByteString(_))
              .via(
                Framing
                  .delimiter(ByteString("\n"), 100, true)
                  .grouped(1000)
              )
              .mapAsync(1)((lines: Seq[ByteString]) =>
                gformConnector.saveDBLookupIds(
                  collectionName,
                  lines
                    .map(_.utf8String)
                    .filter(_.trim().nonEmpty)
                    .map(DbLookupId.apply)
                )
              )
              .toMat(Sink.ignore)(Keep.right)
              .run()
              .map { _ =>
                Created(s"Uploaded db lookup file for collection $collectionName")
              }
              .recover { case e =>
                logger.info(s"Failed to send uploaded file to gforms for collection $collectionName", e)
                InternalServerError(
                  s"Failed to send uploaded file to gforms for collection $collectionName [error=$e]"
                )
              }
          }
          .getOrElse(Future.successful(BadRequest("'file' param is missing")))
      }
    }

  def uploadGformTemplates() = authorizedWrite.async(parse.multipartFormData) { implicit request =>
    val file = temporaryFileToPath(request.body.file("file").get.ref)
    batchUploadService.uploadZip(file.toFile).map { result =>
      Redirect(routes.GformsController.uploadGformTemplatesStatus)
    }
  }

  def uploadGformTemplatesStatus() = authorizedWrite.async { implicit request =>
    Future.successful {
      Ok(
        batch_upload(batchUploadService.processedTemplates.toList, batchUploadService.done)
      )
    }
  }

  val gFormForm: Form[GformId] = Form(
    mapping("formTemplateId" -> mapping("value" -> text)(FormTemplateId.apply)(FormTemplateId.unapply))(GformId.apply)(
      GformId.unapply
    )
  )

  val gformFormTemplatesWithPIIInTitleForm: Form[FormTemplatesWithPIIInTitleForm] = Form(
    mapping("filters" -> text)(FormTemplatesWithPIIInTitleForm.apply)(
      FormTemplatesWithPIIInTitleForm.unapply
    )
  )

  val gformFormTemplateWithPIIInTitleForm: Form[FormTemplateWithPIIInTitleForm] = Form(
    mapping(
      "filters"        -> text,
      "formTemplateId" -> mapping("value" -> text)(FormTemplateId.apply)(FormTemplateId.unapply)
    )(FormTemplateWithPIIInTitleForm.apply)(FormTemplateWithPIIInTitleForm.unapply)
  )

  val handlebarsForm: Form[FormTemplateId] = Form(
    mapping("handlebarsTemplateId" -> text)(FormTemplateId.apply)(
      FormTemplateId.unapply
    )
  )

  val handlebarsSchemaForm: Form[FormTemplateId] = Form(
    mapping("handlebarsSchemaId" -> text)(FormTemplateId.apply)(
      FormTemplateId.unapply
    )
  )

  def getHandlebarsTemplate =
    authorizedRead.async { implicit request =>
      val username = request.retrieval.value
      handlebarsForm
        .bindFromRequest()
        .fold(
          formWithErrors => Future.successful(BadRequest(gform_page(gFormForm))),
          handlebarsId => {
            logger.info(s"$username Queried for ${handlebarsId.value}")
            gformConnector
              .getHandlebarsTemplate(handlebarsId)
              .map { content =>
                Ok(handlebars_template(handlebarsId, Html(content.getOrElse(""))))
              }
          }
        )
    }

  def deleteHandlebarsTemplate =
    authorizedDelete.async { implicit request =>
      val username = request.retrieval.value
      handlebarsForm
        .bindFromRequest()
        .fold(
          formWithErrors => Future.successful(BadRequest(gform_page(gFormForm))),
          handlebarsId => {
            logger.info(s"$username deleted ${handlebarsId.value} ")
            gformConnector.deleteHandlebarsTemplate(handlebarsId).map { deleteResults =>
              Ok(Json.toJson(deleteResults))
            }
          }
        )
    }

  def getHandlebarsSchema =
    authorizedRead.async { implicit request =>
      val username = request.retrieval.value
      handlebarsSchemaForm
        .bindFromRequest()
        .fold(
          formWithErrors => Future.successful(BadRequest(gform_page(gFormForm))),
          handlebarsSchemaId => {
            logger.info(s"$username Queried for ${handlebarsSchemaId.value}")
            gformConnector.getHandlebarsSchema(handlebarsSchemaId).map {
              case Left(ex) =>
                Ok(s"Problem when fetching form template: ${handlebarsSchemaId.value}. Reason: $ex")
              case Right(r) => Ok(Json.prettyPrint(r.schema))
            }
          }
        )
    }

  def deleteHandlebarsSchema =
    authorizedDelete.async { implicit request =>
      val username = request.retrieval.value
      handlebarsSchemaForm
        .bindFromRequest()
        .fold(
          formWithErrors => Future.successful(BadRequest(gform_page(gFormForm))),
          handlebarsSchemaId => {
            logger.info(s"$username deleted ${handlebarsSchemaId.value} ")
            gformConnector.deleteHandlebarsSchema(handlebarsSchemaId).map { deleteResults =>
              Ok(Json.toJson(deleteResults))
            }
          }
        )
    }
}
