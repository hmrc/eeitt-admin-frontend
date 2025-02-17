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

package uk.gov.hmrc.eeittadminfrontend.binders

import cats.implicits._
import org.http4s.Uri
import play.api.libs.json.{ JsError, JsString, JsSuccess, Reads }
import play.api.mvc.{ PathBindable, QueryStringBindable }
import uk.gov.hmrc.eeittadminfrontend.deployment.GithubPath.{ HandlebarsPath, HandlebarsSchemaPath, RootPath }
import uk.gov.hmrc.eeittadminfrontend.deployment.{ Filename, GithubPath }
import uk.gov.hmrc.eeittadminfrontend.history.HistoryId
import uk.gov.hmrc.eeittadminfrontend.models.{ BannerId, FormId, FormTemplateId, FormTemplateRawId, ShutterMessageId }
import uk.gov.hmrc.eeittadminfrontend.models.fileupload.EnvelopeId
import uk.gov.hmrc.eeittadminfrontend.models.sdes.{ NotificationStatus, ProcessingStatus, SdesConfirmationType, SdesDestination }
import uk.gov.hmrc.eeittadminfrontend.models.sdes.ProcessingStatus.Implicits.format

object ValueClassBinders {
  implicit val historyIdBinder: PathBindable[HistoryId] = valueClassBinder(_.value)
  implicit val formTemplateRawIdBinder: PathBindable[FormTemplateRawId] = valueClassBinder(_.value)
  implicit val formTemplateIdBinder: PathBindable[FormTemplateId] = valueClassBinder(_.value)
  implicit val envelopeIdBinder: PathBindable[EnvelopeId] = valueClassBinder(_.value)
  implicit val filenameBinder: PathBindable[Filename] = valueClassBinder(_.value)
  implicit val formIdBinder: PathBindable[FormId] = valueClassBinder(_.value)
  implicit val bannerIdBinder: PathBindable[BannerId] = valueClassBinder(_.value)
  implicit val shutterMessageIdBinder: PathBindable[ShutterMessageId] = valueClassBinder(_.value)
  implicit val sdesConfirmationTypePathBinder: PathBindable[SdesConfirmationType] =
    new PathBindable[SdesConfirmationType] {
      override def bind(key: String, value: String): Either[String, SdesConfirmationType] =
        value match {
          case SdesConfirmationType(s) => s.asRight
          case _ =>
            s"'$value' is not a valid SdesConfirmationType. Valid values are: ${SdesConfirmationType.all}".asLeft
        }

      override def unbind(key: String, value: SdesConfirmationType): String = value.toString
    }

  implicit val githubPathQueryBinder: QueryStringBindable[GithubPath] = new QueryStringBindable[GithubPath] {
    override def unbind(key: String, githubPath: GithubPath): String = {
      val value = githubPath match {
        case HandlebarsPath       => "handlebars"
        case HandlebarsSchemaPath => "jsonSchemas"
        case RootPath             => "formTemplates"
      }
      s"$key=$value"
    }

    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, GithubPath]] =
      params.get(key).flatMap(_.headOption).map {
        case "handlebars"    => Right(HandlebarsPath)
        case "jsonSchemas"   => Right(HandlebarsSchemaPath)
        case "formTemplates" => Right(RootPath)
        case unknown         => throw new IllegalArgumentException(s"Query param $key has invalid value $unknown")
      }
  }

  implicit val uriBinder: QueryStringBindable[Uri] = new QueryStringBindable.Parsing(
    uri => Uri.fromString(uri).toOption.get,
    _.renderString,
    (message, exception) => "Failed to bind Uri: " + message + ", exception " + exception.getMessage()
  )

  implicit val notificationStatusBinder: QueryStringBindable[NotificationStatus] = valueClassQueryBinder(
    NotificationStatus.fromName
  )
  implicit val sdesConfirmationTypeBinder: QueryStringBindable[SdesConfirmationType] = valueClassQueryBinder(
    SdesConfirmationType.fromName
  )
  implicit val sdesDestinationBinder: QueryStringBindable[SdesDestination] = valueClassQueryBinder(
    SdesDestination.fromName
  )
  implicit val processingStatusBinder: QueryStringBindable[ProcessingStatus] = valueClassQueryBinder(_.name)

  def valueClassBinder[A: Reads](fromAtoString: A => String)(implicit stringBinder: PathBindable[String]) = {

    def parseString(str: String) =
      JsString(str).validate[A] match {
        case JsSuccess(a, _) => Right(a)
        case JsError(error)  => Left(s"No valid value in path: $str. Error: $error")
      }

    new PathBindable[A] {
      override def bind(key: String, value: String): Either[String, A] =
        stringBinder.bind(key, value).flatMap(parseString)

      override def unbind(key: String, a: A): String =
        stringBinder.unbind(key, fromAtoString(a))
    }
  }

  private def valueClassQueryBinder[A: Reads](
    fromAtoString: A => String
  )(implicit stringBinder: QueryStringBindable[String]) =
    new QueryStringBindable[A] {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, A]] =
        stringBinder.bind(key, params).map(_.flatMap(parseString[A]))

      override def unbind(key: String, a: A): String =
        stringBinder.unbind(key, fromAtoString(a))
    }

  private def parseString[A: Reads](str: String) =
    JsString(str).validate[A] match {
      case JsSuccess(a, _) => Right(a)
      case JsError(error)  => Left("No valid value in url binding: " + str + ". Error: " + error)
    }

}
