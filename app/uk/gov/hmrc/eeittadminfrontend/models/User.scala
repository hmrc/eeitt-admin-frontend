/*
 * Copyright 2018 HM Revenue & Customs
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

import play.api.Logger
import play.api.libs.json._

case class User(email: Email, permission: Seq[Permission] = Seq(QueryPermission)) {
  def this(email: Email) = this(email, Seq(QueryPermission))
}

object User {
  implicit val format: OFormat[User] = Json.format[User]
}

case class Email(value: String)

object Email {
  implicit val format: OFormat[Email] = Json.format[Email]
}

case class Permission(value: String)
object QueryPermission extends Permission("Query")
object DeltasPermission extends Permission("Deltas")
object GFormsPermission extends Permission("Gforms")

object Permission {

  implicit val format: Format[Permission] = new Format[Permission] {
    override def reads(json: JsValue): JsResult[Permission] = {
      println("BOB" + Json.prettyPrint(json))
      (json \ "value").getOrElse(JsString("Error")) match {
        case JsString("Query")  => JsSuccess(QueryPermission)
        case JsString("Deltas") => JsSuccess(DeltasPermission)
        case JsString("Gforms") => JsSuccess(GFormsPermission)
        case _ =>
          Logger.error("Some error")
          JsError("Some error")
      }
    }

    override def writes(o: Permission) =
      o match {
        case QueryPermission  => Json.obj("value" -> "Query")
        case DeltasPermission => Json.obj("value" -> "Deltas")
        case GFormsPermission => Json.obj("value" -> "Gforms")
        case _ =>
          Logger.error("Some writes Error")
          JsString("Error")
      }
  }
}
