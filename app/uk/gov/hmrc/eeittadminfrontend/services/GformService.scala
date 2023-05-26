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

package uk.gov.hmrc.eeittadminfrontend.services

import cats.data.EitherT
import io.circe.Json
import cats.syntax.all._

import javax.inject.Inject
import org.slf4j.{ Logger, LoggerFactory }
import play.api.libs.json.{ JsArray, JsString }

import scala.concurrent.{ ExecutionContext, Future }
import uk.gov.hmrc.eeittadminfrontend.connectors.GformConnector
import uk.gov.hmrc.eeittadminfrontend.deployment.ContentValue.JsonContent
import uk.gov.hmrc.eeittadminfrontend.deployment.{ ContentValue, Filename, GithubContent, MongoContent }
import uk.gov.hmrc.eeittadminfrontend.models.{ CircePlayHelpers, DeleteResult, DeleteResults, FormTemplateId }
import uk.gov.hmrc.http.HeaderCarrier

class GformService @Inject() (gformConnector: GformConnector) {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  def getAllGformsTemplates(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): Future[Either[String, List[FormTemplateId]]] =
    gformConnector.getAllGformsTemplates.map {
      case JsArray(formTemplateIds) =>
        val ftIds: Seq[FormTemplateId] = formTemplateIds.collect {
          case JsString(id) if !id.startsWith("specimen-") => FormTemplateId(id)
        }.toSeq
        Right(ftIds.sortBy(_.value).toList)

      case other => Left("Cannot retrieve form templates. Expected JsArray, got: " + other)
    }

  def getFormTemplate(formTemplateId: FormTemplateId)(implicit
    ec: ExecutionContext
  ): Future[Either[String, MongoContent]] = {
    logger.debug(s"Loading $formTemplateId from MongoDB")
    gformConnector
      .getGformsTemplate(formTemplateId)
      .map(_.map(json => MongoContent(formTemplateId, JsonContent(CircePlayHelpers.playToCirceUnsafe(json)))))
  }

  def saveTemplate(
    formTemplateId: FormTemplateId,
    json: Json
  )(implicit
    ec: ExecutionContext
  ): EitherT[Future, String, Unit] = EitherT(
    gformConnector.saveTemplate(
      formTemplateId,
      CircePlayHelpers.circeToPlayUnsafe(json)
    )
  )

  def deleteTemplate(formTemplateId: FormTemplateId)(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): Future[DeleteResults] = gformConnector.deleteTemplate(formTemplateId)

  def saveHandlebarsTemplate(
    formTemplateId: FormTemplateId,
    payload: String
  )(implicit
    ec: ExecutionContext
  ): EitherT[Future, String, Unit] = EitherT(
    gformConnector.saveHandlebarsTemplate(
      formTemplateId,
      payload
    )
  )

  def getRawHandlebarsTemplate(
    formTemplateId: FormTemplateId
  )(implicit
    ec: ExecutionContext
  ): Future[Either[String, MongoContent]] = {
    logger.debug(s"Loading ${formTemplateId.value} from MongoDB")
    gformConnector
      .getRawHandlebarsTemplate(formTemplateId)
      .map(_.map(res => MongoContent(formTemplateId, ContentValue.TextContent(res))))
  }

  def getHandlebarsTemplate(
    formTemplateId: FormTemplateId
  )(implicit
    ec: ExecutionContext
  ): Future[Either[String, MongoContent]] = {
    logger.debug(s"Loading ${formTemplateId.value} from MongoDB")
    gformConnector
      .getHandlebarsTemplate(formTemplateId)
      .map(_.map(res => MongoContent(formTemplateId, ContentValue.TextContent(res))))
  }

  def deleteHandlebarsTemplate(
    formTemplateId: FormTemplateId
  )(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): Future[DeleteResult] = {
    logger.debug(s"Loading ${formTemplateId.value} from MongoDB")
    gformConnector
      .deleteHandlebarsTemplate(formTemplateId)
  }

  def getAllHandlebarsTemplates(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): Future[Either[String, List[FormTemplateId]]] =
    gformConnector.getAllHandlebarsTemplates.map {
      case JsArray(templates) =>
        val templateIds = templates.collect { case JsString(template) =>
          FormTemplateId(template)
        }
        Right(templateIds.toList)
      case _ =>
        Right(List.empty[FormTemplateId])
    }

  def getHandlebarsTemplateIds(formTemplateId: FormTemplateId)(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): Future[Either[String, List[FormTemplateId]]] =
    gformConnector.getHandlebarsTemplateIds(formTemplateId).map {
      case JsArray(templates) =>
        val templateIds = templates.collect { case JsString(template) =>
          FormTemplateId(template)
        }
        Right(templateIds.toList)
      case _ =>
        Right(List.empty[FormTemplateId])
    }

  def retrieveContentsForHandlebars(
    formTemplateId: FormTemplateId,
    githubContents: List[(Filename, GithubContent)],
    isJson: Boolean
  )(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): Future[Either[String, (List[MongoContent], List[(Filename, GithubContent)])]] =
    if (isJson) {
      (for {
        templateHandlebarsIds <- EitherT(getHandlebarsTemplateIds(formTemplateId))
        mongoHandlebarsIds    <- EitherT(getAllHandlebarsTemplates)
        handlebars =
          templateHandlebarsIds
            .traverse(handlebarsTemplateId => mongoHandlebarsIds.find(_ === handlebarsTemplateId))
            .getOrElse(List.empty[FormTemplateId])
        mongoContentsForHandlebars <- handlebars.traverse(id => EitherT(getRawHandlebarsTemplate(id)))
        res <- EitherT.fromEither[Future] {
                 mongoContentsForHandlebars
                   .traverse { id =>
                     githubContents
                       .find(_._1.value === s"${id.formTemplateId.value}.hbs")
                       .map(_._2)
                       .toRight(s"Github content not found for handlebars template with ID: ${id.formTemplateId.value}")
                   }
               }
        githubContents2 = res.map(g => (Filename(s"${g.formTemplateId.value}.hbs") -> g))
      } yield mongoContentsForHandlebars -> githubContents2).value
    } else Future.successful(Right((List.empty[MongoContent], List.empty[(Filename, GithubContent)])))
}
