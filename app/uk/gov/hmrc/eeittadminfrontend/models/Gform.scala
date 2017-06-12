/*
 * Copyright 2017 HM Revenue & Customs
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


/**
  * Created by harrison on 06/06/17.
  */
case class FormTypeId(value: String)extends AnyVal{
  override def toString = value
}

object FormTypeId{
  implicit val format: Format[FormTypeId] = ValueClassFormatter.format(FormTypeId.apply)(_.value)
}

case class GformIdAndVersion(formTypeId: FormTypeId, version: String)
