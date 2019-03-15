/*
 * Copyright 2019 HM Revenue & Customs
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

import play.api.data.Form
import play.api.data.Forms.{ mapping, text }
import play.api.libs.json
import play.api.libs.json.{ Format, Json }

case class MigrationData(groupId: String, identifiers: List[Identifier], verifiers: List[Verifier])

object MigrationData {
  implicit val format: Format[MigrationData] = json.Json.format[MigrationData]
}

case class MigrationDataDeallocate(groupId: String, identifiers: List[Identifier])

object MigrationDataDeallocate {
  implicit val format: Format[MigrationDataDeallocate] = json.Json.format[MigrationDataDeallocate]
}

case class MigrationDataQuery(groupId: String)

object MigrationDataQuery {
  implicit val format: Format[MigrationDataQuery] = json.Json.format[MigrationDataQuery]
}

case class DeleteKnownFactsRequest(request: Either[List[Identifier], Exception])

object DeleteKnownFactsRequest {
  def apply(identifiers: String): DeleteKnownFactsRequest =
    try {
      Json
        .fromJson[List[Identifier]](Json.parse(identifiers))
        .fold(
          invalid => DeleteKnownFactsRequest(Right(new Exception(invalid.toString()))),
          valid => DeleteKnownFactsRequest(Left(valid))
        )
    } catch {
      case e: Exception => DeleteKnownFactsRequest(Right(e))
    }

  def toStrings(d: DeleteKnownFactsRequest): Option[String] = d.request match {
    case Left(x)  => Some(Json.toJson(x).toString())
    case Right(e) => None
  }

  val deleteKnownFactsRequestForm: Form[DeleteKnownFactsRequest] = Form(
    mapping("identifiers" -> text)(DeleteKnownFactsRequest.apply)(DeleteKnownFactsRequest.toStrings))
}
