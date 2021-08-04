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

package uk.gov.hmrc.eeittadminfrontend.services

import cats.data.EitherT
import io.circe.Json
import org.slf4j.{ Logger, LoggerFactory }
import play.api.libs.json.{ JsArray, JsString }
import play.api.mvc.Result
import scala.concurrent.{ ExecutionContext, Future }
import uk.gov.hmrc.eeittadminfrontend.connectors.GformConnector
import uk.gov.hmrc.eeittadminfrontend.deployment.MongoContent
import uk.gov.hmrc.eeittadminfrontend.models.{ CircePlayHelpers, FormTemplateId }
import uk.gov.hmrc.http.HeaderCarrier

class GformService(gformConnector: GformConnector) {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  def getAllGformsTemplates(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): Future[Either[String, List[FormTemplateId]]] =
    gformConnector.getAllGformsTemplates.map {
      case JsArray(formTemplateIds) =>
        val ftIds: Seq[FormTemplateId] = formTemplateIds.collect {
          case JsString(id) if !id.startsWith("specimen-") => FormTemplateId(id)
        }
        Right(ftIds.sortBy(_.value).toList)

      case other => Left("Cannot retrieve form templates. Expected JsArray, got: " + other)
    }

  def getFormTemplate(formTemplateId: FormTemplateId)(implicit
    ec: ExecutionContext
  ): Future[Either[String, MongoContent]] = {
    logger.debug(s"Loading $formTemplateId from MongoDB")
    gformConnector
      .getGformsTemplate(formTemplateId)
      .map {
        case Left(error) => Left(error.error)
        case Right(json) => Right(MongoContent(formTemplateId, CircePlayHelpers.playToCirceUnsafe(json)))
      }
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
  ): Future[Result] = gformConnector.deleteTemplate(formTemplateId)

}
