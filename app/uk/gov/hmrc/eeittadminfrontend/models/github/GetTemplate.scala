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

package uk.gov.hmrc.eeittadminfrontend.models.github

import github4s.domain.Content
import uk.gov.hmrc.eeittadminfrontend.models.FormTemplateId

sealed trait GetTemplate extends Product with Serializable {

  def formTemplateId: FormTemplateId

  def fold[B](f: GetTemplate.NewFile => B)(g: GetTemplate.Exists => B)(h: GetTemplate.NoGithubEnabled => B): B =
    this match {
      case gt: GetTemplate.NewFile         => f(gt)
      case gt: GetTemplate.Exists          => g(gt)
      case gt: GetTemplate.NoGithubEnabled => h(gt)
    }
}

object GetTemplate {
  case class NewFile(formTemplateId: FormTemplateId) extends GetTemplate
  case class Exists(formTemplateId: FormTemplateId, content: Content) extends GetTemplate
  case class NoGithubEnabled(formTemplateId: FormTemplateId) extends GetTemplate

}
