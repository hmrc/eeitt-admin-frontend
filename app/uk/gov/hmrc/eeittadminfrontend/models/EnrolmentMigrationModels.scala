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
    try Json
      .fromJson[List[Identifier]](Json.parse(identifiers))
      .fold(
        invalid => DeleteKnownFactsRequest(Right(new Exception(invalid.toString()))),
        valid => DeleteKnownFactsRequest(Left(valid))
      )
    catch {
      case e: Exception => DeleteKnownFactsRequest(Right(e))
    }

  def toStrings(d: DeleteKnownFactsRequest): Option[String] =
    d.request match {
      case Left(x)  => Some(Json.toJson(x).toString())
      case Right(e) => None
    }

  val deleteKnownFactsRequestForm: Form[DeleteKnownFactsRequest] = Form(
    mapping("identifiers" -> text)(DeleteKnownFactsRequest.apply)(DeleteKnownFactsRequest.toStrings)
  )
}

case class TaxEnrolmentRequest(request: String)

object TaxEnrolmentRequest {
  val knownFactsForm: Form[TaxEnrolmentRequest] = Form(
    mapping("identifiers" -> text)(TaxEnrolmentRequest.apply)(TaxEnrolmentRequest.unapply)
  )
}

case class AllEnrolmentRequest(regimeId: String)

object AllEnrolmentRequest {
  val allEnrolmentsForm: Form[AllEnrolmentRequest] = Form(
    mapping("regimeId" -> text)(AllEnrolmentRequest.apply)(AllEnrolmentRequest.unapply)
  )
}

case class UpsertRequest(identifiers: List[Identifier], verifiers: List[Verifier])

object UpsertRequest {
  implicit val format: Format[UpsertRequest] = json.Json.format[UpsertRequest]
}

case class UpsertKnownFactsRequest(request: Either[UpsertRequest, Exception])

object UpsertKnownFactsRequest {
  def apply(taxEnrolment: String): UpsertKnownFactsRequest = {
    println("sadsada" + taxEnrolment)
    try Json
      .fromJson[UpsertRequest](Json.parse(taxEnrolment))
      .fold(
        invalid => UpsertKnownFactsRequest(Right(new Exception(invalid.toString()))),
        valid => UpsertKnownFactsRequest(Left(UpsertRequest(valid.identifiers, valid.verifiers)))
      )
    catch {
      case e: Exception => UpsertKnownFactsRequest(Right(e))
    }
  }

  def toStrings(u: UpsertKnownFactsRequest): Option[String] =
    u.request match {
      case Left(x)  => Some(Json.toJson(TaxEnrolment(x.identifiers, x.verifiers)).toString())
      case Right(e) => None
    }

  val upsertKnownFactsRequestForm: Form[UpsertKnownFactsRequest] = Form(
    mapping("identifiersverifiers" -> text)(UpsertKnownFactsRequest.apply)(UpsertKnownFactsRequest.toStrings)
  )
}

case class UserDetailsData(credentialRole: String, gatewayId: String)

object UserDetailsData {
  implicit val format: Format[UserDetailsData] = json.Json.format[UserDetailsData]
}
