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

trait AttachmentCheck extends Product with Serializable
object AttachmentCheck {
  case object CountOk extends AttachmentCheck
  case class CountDoesNotMatch(envelopeCount: Int) extends AttachmentCheck
  case object EnvelopeDoesNotExists extends AttachmentCheck
  case object CannotParseEnvelope extends AttachmentCheck
}
