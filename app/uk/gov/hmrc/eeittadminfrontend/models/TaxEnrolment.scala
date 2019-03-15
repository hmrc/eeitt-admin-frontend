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

import play.api.libs.json.Json

case class Identifier(key: String, value: String)

object Identifier {
  implicit val format = Json.format[Identifier]
}

case class Identifiers(identifiers: List[Identifier])

object Identifiers {
  implicit val format = Json.format[Identifiers]
}

case class Verifier(key: String, value: String)

object Verifier {
  implicit val format = Json.format[Verifier]
}

case class Verifiers(verifiers: List[Verifier])

object Verifiers {
  implicit val format = Json.format[Verifiers]
}

case class TaxEnrolmentPayload(verifiers: List[Verifier], `type`: String, userId: String, friendlyName: String)

object TaxEnrolmentPayload {
  implicit val format = Json.format[TaxEnrolmentPayload]
}

case class TaxEnrolment(identifiers: List[Identifier], verifiers: List[Verifier])

object TaxEnrolment {
  implicit val format = Json.format[TaxEnrolment]

  val service = "HMRC-OBTDS-ORG"

  def enrolmentKey(identifiers: List[Identifier]): String = {
    val elements: List[String] = service :: identifiers.sortBy(_.key).flatMap(i => List(i.key, i.value))
    elements.mkString("~")
  }
}

case class KnownFact(key: String, value: String)

object KnownFact {
  implicit val format = Json.format[KnownFact]
}

case class ServiceQuery(service: String, knownFacts: List[KnownFact])

object ServiceQuery {
  implicit val format = Json.format[ServiceQuery]
}
