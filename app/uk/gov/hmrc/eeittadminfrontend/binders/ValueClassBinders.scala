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

import org.http4s.Uri
import play.api.libs.json.{ JsError, JsString, JsSuccess, Reads }
import play.api.mvc.{ PathBindable, QueryStringBindable }
import uk.gov.hmrc.eeittadminfrontend.deployment.Filename
import uk.gov.hmrc.eeittadminfrontend.models.{ BannerId, FormId, FormTemplateId }
import uk.gov.hmrc.eeittadminfrontend.models.fileupload.EnvelopeId
import uk.gov.hmrc.eeittadminfrontend.models.sdes.{ NotificationStatus, ProcessingStatus }
import uk.gov.hmrc.eeittadminfrontend.models.sdes.ProcessingStatus.Implicits.format

object ValueClassBinders {
  implicit val formTemplateIdBinder: PathBindable[FormTemplateId] = valueClassBinder(_.value)
  implicit val envelopeIdBinder: PathBindable[EnvelopeId] = valueClassBinder(_.value)
  implicit val filenameBinder: PathBindable[Filename] = valueClassBinder(_.value)
  implicit val formIdBinder: PathBindable[FormId] = valueClassBinder(_.value)
  implicit val bannerIdBinder: PathBindable[BannerId] = valueClassBinder(_.value)

  implicit val uriBinder: QueryStringBindable[Uri] = new QueryStringBindable.Parsing(
    uri => Uri.fromString(uri).toOption.get,
    _.renderString,
    (message, exception) => "Failed to bind Uri: " + message + ", exception " + exception.getMessage()
  )

  implicit val notificationStatusBinder: QueryStringBindable[NotificationStatus] = valueClassQueryBinder(
    NotificationStatus.fromName
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
