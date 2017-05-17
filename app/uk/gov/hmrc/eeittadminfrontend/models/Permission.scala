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

import play.api.libs.json.{JsValue, Json, OFormat, Reads}

case class Permission(value : String)
object QueryPermission extends Permission("Query")
object DeltasPermission extends Permission("Deltas")
object GFormsPermission extends Permission("GForms")
object MaintenancePermission extends Permission("Maintenance")


object Permission {

//  implicit val reads = new Reads[Permission] {
//    override def reads(json: JsValue) = {
//      json
//    }
//  }
  implicit val format: OFormat[Permission] = Json.format[Permission]
}