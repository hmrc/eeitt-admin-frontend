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

package uk.gov.hmrc.eeittadminfrontend.controllers

import cats._
import cats.syntax.all._
import cats.data.{ EitherT, NonEmptyList }
import cats.effect.IO
import github4s.domain.Content
import org.slf4j.{ Logger, LoggerFactory }
import play.api.i18n.I18nSupport
import play.api.mvc.MessagesControllerComponents
import play.twirl.api.Html
import scala.concurrent.{ ExecutionContext, Future }
import uk.gov.hmrc.eeittadminfrontend.auth.AuthConnector
import uk.gov.hmrc.eeittadminfrontend.config.{ AppConfig, AuthAction }
import uk.gov.hmrc.eeittadminfrontend.deployment.{ DownloadUrl, Filename, MongoContent, Reconciliation, ReconciliationLookup }
import uk.gov.hmrc.eeittadminfrontend.diff.DiffMaker
import uk.gov.hmrc.eeittadminfrontend.models.FormTemplateId
import uk.gov.hmrc.eeittadminfrontend.models.github.PrettyPrintJson
import uk.gov.hmrc.eeittadminfrontend.services.{ GformService, GithubService }
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

class DeploymentController(
  val authConnector: AuthConnector,
  authAction: AuthAction,
  gformService: GformService,
  githubService: GithubService,
  messagesControllerComponents: MessagesControllerComponents
)(implicit ec: ExecutionContext, appConfig: AppConfig)
    extends FrontendController(messagesControllerComponents) with I18nSupport {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  private val ioToFuture: IO ~> Future = new (IO ~> Future) {
    def apply[A](fa: IO[A]): Future[A] = fa.unsafeToFuture()
  }

  def download(formTemplateId: FormTemplateId) = authAction.async { request =>
    EitherT(gformService.getFormTemplate(formTemplateId)).fold(
      error => Ok(s"Problem when fetching form template: $formTemplateId. Reason: $error"),
      mongoContent => Ok(PrettyPrintJson.asString(mongoContent.json))
    )
  }

  def deployFilename(downloadUrl: DownloadUrl, filename: Filename) = authAction.async { implicit request =>
    def logDeploymentStatus(message: String): Unit =
      logger.info(s"${request.userData} deployment of filename ${filename.value} " + message)

    logDeploymentStatus("started")
    (for {
      githubContent <- githubService.retrieveFormTemplate(downloadUrl).mapK(ioToFuture)
      _             <- gformService.saveTemplate(githubContent)
    } yield githubContent.formTemplateId).fold(
      error => {
        logDeploymentStatus(s"failed with: $error")
        BadRequest(error)
      },
      formTemplateId => {
        logDeploymentStatus(formTemplateId.value + " successfully deployed")
        Ok(uk.gov.hmrc.eeittadminfrontend.views.html.deployment_success(formTemplateId, filename))
      }
    )
  }

  def deploymentExisting(
    formTemplateId: FormTemplateId,
    filename: Filename
  ) =
    authAction.async { implicit request =>
      (
        EitherT(gformService.getFormTemplate(formTemplateId)),
        githubService.getCommit(filename).mapK(ioToFuture),
        githubService.retrieveFilenameData(filename).mapK(ioToFuture)
      ).parMapN { case (mongoTemplate, commit, (downloadUrl, githubTemplate)) =>
        val diff = DiffMaker.getDiff(filename, mongoTemplate, githubTemplate).replace("'", "\\'")

        Ok(
          uk.gov.hmrc.eeittadminfrontend.views.html
            .deployment_existing(
              formTemplateId,
              Html(diff),
              commit,
              downloadUrl,
              filename
            )
        )
      }.fold(error => BadRequest(error), identity)
    }

  def delete(formTemplateId: FormTemplateId) =
    authAction.async { implicit request =>
      logger.info(s"${request.userData} is deleting ${formTemplateId.value}")
      gformService.deleteTemplate(formTemplateId)
    }

  def deploymentDeleted(formTemplateId: FormTemplateId) = authAction.async { implicit request =>
    Future.successful(Ok(uk.gov.hmrc.eeittadminfrontend.views.html.deployment_deleted(formTemplateId)))
  }

  def deploymentNew(
    formTemplateId: FormTemplateId,
    filename: Filename
  ) =
    authAction.async { implicit request =>
      (
        githubService.getCommit(filename),
        githubService.retrieveFilenameData(filename)
      ).parMapN { case (commit, (downloadUrl, githubTemplate)) =>
        Ok(
          uk.gov.hmrc.eeittadminfrontend.views.html
            .deployment_new(formTemplateId, commit, downloadUrl, filename)
        )
      }.mapK(ioToFuture)
        .fold(error => BadRequest(error), identity)
    }

  def deployments =
    authAction.async { implicit request =>
      EitherT(gformService.getAllGformsTemplates)
        .flatMap { mongoTemplateIds =>
          logger.info(s"${request.userData} Loading ${mongoTemplateIds.size} templates from MongoDB")
          val mongoTemplates: EitherT[Future, String, List[MongoContent]] =
            mongoTemplateIds.parTraverse { formTemplateId =>
              EitherT(gformService.getFormTemplate(formTemplateId))
            }

          (mongoTemplates, githubService.listTemplates().mapK(ioToFuture))
            .parMapN((_, _))
            .flatMap { case (mongoTemplates, githubTemplates) =>
              logger.info(s"${request.userData} Loading ${githubTemplates.size} templates from Github")

              toReconciliationLookup(mongoTemplates, githubTemplates)
                .mapK(ioToFuture)
                .map(reconciliationLookup =>
                  Ok(
                    uk.gov.hmrc.eeittadminfrontend.views.html
                      .deployments(
                        reconciliationLookup,
                        existingTemplatesTable(reconciliationLookup.existingTemplates),
                        newTemplatesTable(reconciliationLookup.newTemplates)
                      )
                  )
                )
            }
        }
        .fold(error => BadRequest(error), identity)
    }

  private def toReconciliationLookup(
    mongoTemplates: List[MongoContent],
    githubTemplates: NonEmptyList[Content]
  ): EitherT[IO, String, ReconciliationLookup] = {

    val mongoLookup: Map[FormTemplateId, MongoContent] =
      mongoTemplates.map(a => a.formTemplateId -> a).toMap

    val reconciliationsNel: EitherT[IO, String, NonEmptyList[Reconciliation]] =
      githubTemplates
        .parTraverse { githubTemplate =>
          EitherT.fromEither[IO](DownloadUrl.fromContent(githubTemplate)).flatMap { downloadUrl =>
            reconciliation(
              mongoLookup,
              downloadUrl.stripTokenQueryParam,
              Filename(githubTemplate.name)
            )
          }
        }

    reconciliationsNel.map { reconciliations =>
      val n: List[Reconciliation.New] = reconciliations.collect { case r: Reconciliation.New => r }
      val e: List[Reconciliation.Existing] = reconciliations.collect { case r: Reconciliation.Existing => r }

      val allGithubTemplatesId: NonEmptyList[FormTemplateId] = reconciliations.map(_.formTemplateId)
      val deletedTemplates: List[Reconciliation.Deleted] =
        mongoLookup.keys.toList
          .filter(ftId => !allGithubTemplatesId.contains_(ftId))
          .sortBy(_.value)
          .map(formTemplateId =>
            Reconciliation
              .Deleted(formTemplateId, routes.DeploymentController.deploymentDeleted(formTemplateId))
          )

      ReconciliationLookup(n.groupBy(_.formTemplateId), e.groupBy(_.formTemplateId), deletedTemplates)
    }
  }

  private def reconciliation(
    mongoLookup: Map[FormTemplateId, MongoContent],
    downloadUrl: DownloadUrl,
    filename: Filename
  ): EitherT[IO, String, Reconciliation] =
    githubService.retrieveFormTemplate(downloadUrl).map { githubContent =>
      val formTemplateId = githubContent.formTemplateId
      mongoLookup.get(formTemplateId) match {
        case None =>
          Reconciliation.New(
            formTemplateId,
            filename,
            routes.DeploymentController.deploymentNew(
              formTemplateId,
              filename
            )
          )
        case Some(mongoContent) =>
          val inSync = DiffMaker.inSync(mongoContent, githubContent)
          Reconciliation.Existing(
            formTemplateId,
            filename,
            routes.DeploymentController
              .deploymentExisting(formTemplateId, filename),
            inSync
          )
      }
    }
  import uk.gov.hmrc.govukfrontend.views.html.components._

  private def existingTemplatesTable(existing: Map[FormTemplateId, List[Reconciliation.Existing]]): Table =
    Table(
      rows =
        for {
          (_, reconciliations) <- existing.toList.sortBy(_._1.value)
          reconciliation       <- reconciliations
        } yield Seq(
          TableRow(
            content = HtmlContent(uk.gov.hmrc.eeittadminfrontend.views.html.deployment_link(reconciliation))
          ),
          TableRow(
            content = Text(reconciliation.filename.value)
          ),
          TableRow(
            content = Text(if (reconciliation.inSync) "" else "Out of sync")
          )
        ),
      head = Some(
        Seq(
          HeadCell(
            content = Text("Template Id")
          ),
          HeadCell(
            content = Text("Filename")
          ),
          HeadCell(
            content = Text("Status")
          )
        )
      ),
      firstCellIsHeader = true
    )

  private def newTemplatesTable(reconciliationNew: Map[FormTemplateId, List[Reconciliation.New]]): Table =
    Table(
      rows =
        for {
          (_, reconciliations) <- reconciliationNew.toList.sortBy(_._1.value)
          reconciliation       <- reconciliations
        } yield Seq(
          TableRow(
            content = HtmlContent(uk.gov.hmrc.eeittadminfrontend.views.html.deployment_link_new(reconciliation))
          ),
          TableRow(
            content = Text(reconciliation.filename.value)
          )
        ),
      head = Some(
        Seq(
          HeadCell(
            content = Text("Template Id")
          ),
          HeadCell(
            content = Text("Filename")
          )
        )
      ),
      firstCellIsHeader = true
    )

}
