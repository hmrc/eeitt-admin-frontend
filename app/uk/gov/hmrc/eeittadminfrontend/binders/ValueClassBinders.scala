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

package uk.gov.hmrc.eeittadminfrontend.binders

import org.http4s.Uri
import play.api.libs.json.{ JsError, JsString, JsSuccess, Reads }
import play.api.mvc.{ PathBindable, QueryStringBindable }
import uk.gov.hmrc.eeittadminfrontend.deployment.{ DownloadUrl, Filename }
import uk.gov.hmrc.eeittadminfrontend.models.FormTemplateId
import uk.gov.hmrc.eeittadminfrontend.models.fileupload.EnvelopeId

object ValueClassBinders {
  implicit val formTemplateIdBinder: PathBindable[FormTemplateId] = valueClassBinder(_.value)
  implicit val envelopeIdBinder: PathBindable[EnvelopeId] = valueClassBinder(_.value)
  implicit val downloadUrlBinder: PathBindable[DownloadUrl] = valueClassBinder(_.uri.renderString)
  implicit val filenameBinder: PathBindable[Filename] = valueClassBinder(_.value)

  implicit val uriBinder: QueryStringBindable[Uri] = new QueryStringBindable.Parsing(
    uri => Uri.fromString(uri).right.get,
    _.renderString,
    (message, exception) => "Failed to bind Uri: " + message + ", exception " + exception.getMessage()
  )

  def valueClassBinder[A: Reads](fromAtoString: A => String)(implicit stringBinder: PathBindable[String]) = {

    def parseString(str: String) =
      JsString(str).validate[A] match {
        case JsSuccess(a, _) => Right(a)
        case JsError(error)  => Left(s"No valid value in path: $str. Error: $error")
      }

    new PathBindable[A] {
      override def bind(key: String, value: String): Either[String, A] =
        stringBinder.bind(key, value).right.flatMap(parseString)

      override def unbind(key: String, a: A): String =
        stringBinder.unbind(key, fromAtoString(a))
    }
  }
}
