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

import play.api.Logger
import play.api.libs.json._

trait Database
object ETMP extends Database
object Enrollments extends Database
object Error extends Database

object Database {

  implicit val format = new Format[Database] {
    override def reads(json: JsValue): JsResult[Database] = {
      (json \ "database").getOrElse(JsString("Error")) match {
        case JsString("ETMP") => JsSuccess(ETMP)
        case JsString("Enrollments") => JsSuccess(Enrollments)
        case _ => JsError("Bob")
      }
    }

    override def writes(o: Database): JsValue = {
      o match {
        case ETMP => Json.obj("database" -> "ETMP")
        case Enrollments => Json.obj("database" -> "Enrollments")
        case Error =>
          Logger.error("Illegal arguement")
          JsString("Error")
      }
    }
  }
}
