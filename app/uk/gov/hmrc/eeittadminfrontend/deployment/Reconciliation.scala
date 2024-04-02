/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.eeittadminfrontend.deployment

import play.api.mvc.Call
import uk.gov.hmrc.eeittadminfrontend.models.FormTemplateId

sealed trait Reconciliation extends Product with Serializable {
  def formTemplateId: FormTemplateId
  def path: GithubPath
}

object Reconciliation {
  case class New(formTemplateId: FormTemplateId, path: GithubPath, filename: Filename, call: Call)
      extends Reconciliation
  case class Existing(formTemplateId: FormTemplateId, path: GithubPath, filename: Filename, call: Call, inSync: Boolean)
      extends Reconciliation
  case class Deleted(formTemplateId: FormTemplateId, path: GithubPath, call: Call) extends Reconciliation
}
