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

case class KeyValuePair(key: String, value: String)

object KeyValuePair {

  implicit val format = Json.format[KeyValuePair]
}
case class Delete(user: String, enrollmentKey: EnrollmentKey)
case class EnrollmentKey(service: String, identifier: String, value: String)
case class GGKnownFacts(enrollmentKey: EnrollmentKey, verifiers: List[KeyValuePair])
case class Enrollment(user: String, enrollmentKey: EnrollmentKey, verifiers: List[KeyValuePair], friendlyName: String)
