/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.eeittadminfrontend.models

import play.api.libs.json._

case class FormTemplateId(value: String) extends AnyVal {
  override def toString = value
}

object FormTemplateId {
  implicit val format: Format[FormTemplateId] = ValueClassFormatter.format(FormTemplateId.apply)(_.value)
}

case class GformId(formTemplateId: FormTemplateId)

case class GformTemplate(template: JsValue)

object GformTemplate {
  implicit val format: Format[GformTemplate] = Json.format[GformTemplate]
}

case class DbLookupId(_id: String)

object DbLookupId {
  implicit val format: Format[DbLookupId] = Json.format[DbLookupId]
}

case class GformServiceError(statusCode: Int, message: String) extends Exception(message)
