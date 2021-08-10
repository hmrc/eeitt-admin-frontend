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

package uk.gov.hmrc.eeittadminfrontend.validators

import cats.implicits._
import cats.data.Validated
import io.circe.optics.JsonPath._
import io.circe.Json
import uk.gov.hmrc.eeittadminfrontend.connectors.HMRCEmailRendererConnector
import uk.gov.hmrc.eeittadminfrontend.models.email._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

class FormTemplateValidator(hmrcEmailRendererConnector: HMRCEmailRendererConnector)(implicit ec: ExecutionContext) {

  def validate(json: Json)(implicit hc: HeaderCarrier): Future[Either[String, Unit]] = {
    val maybeEmailTemplateIds: Option[List[String]] =
      root.emailTemplateId.obj
        .getOption(json)
        .map(_.toMap.flatMap(_._2.asString.toList).filter(_.nonEmpty).toList)
        .orElse(
          root.emailTemplateId.string.getOption(json).map(List(_))
        )

    val emailTemplateParams: Map[String, String] =
      root.emailParameters.each.emailTemplateVariable.string.getAll(json).map(v => (v, v)).toMap

    maybeEmailTemplateIds.fold(Future.successful(().asRight[String])) { emailTemplateIds =>
      Future
        .sequence(emailTemplateIds.map { emailTemplateId =>
          hmrcEmailRendererConnector
            .renderTemplate(EmailRenderRequest(emailTemplateId, emailTemplateParams))
            .map {
              case Successful => ().asRight[String]
              case NotFound   => s"Email template '$emailTemplateId' not found. ".asLeft[Unit]
              case ParametersNotFound(reason) =>
                s"Email template parameters are missing: $reason. ".asLeft[Unit]
              case Unexpected(reason) => s"$reason. ".asLeft[Unit]
            }
        })
        .map(_.traverse(Validated.fromEither).toEither.right.map(_.head))
    }
  }
}
