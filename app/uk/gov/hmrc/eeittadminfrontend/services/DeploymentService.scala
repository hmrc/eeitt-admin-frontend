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

package uk.gov.hmrc.eeittadminfrontend.services

import javax.inject.Inject
import scala.concurrent.Future
import uk.gov.hmrc.eeittadminfrontend.deployment.DeploymentRecord
import uk.gov.hmrc.eeittadminfrontend.models.FormTemplateId
import uk.gov.hmrc.eeittadminfrontend.repo.DeploymentRepo

class DeploymentService @Inject() (deploymentRepo: DeploymentRepo) {
  def find(formTemplateId: FormTemplateId): Future[List[DeploymentRecord]] = deploymentRepo.get(formTemplateId)
  def save(deploymentRecord: DeploymentRecord): Future[Unit] = deploymentRepo.save(deploymentRecord)
}
