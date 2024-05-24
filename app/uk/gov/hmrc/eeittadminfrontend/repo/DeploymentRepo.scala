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

package uk.gov.hmrc.eeittadminfrontend.repo

import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Sorts.descending

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.{ ExecutionContext, Future }
import uk.gov.hmrc.eeittadminfrontend.models.FormTemplateId
import uk.gov.hmrc.eeittadminfrontend.deployment.DeploymentRecord
import uk.gov.hmrc.mongo.play.PlayMongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

@Singleton
class DeploymentRepo @Inject() (mongoComponent: PlayMongoComponent)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[DeploymentRecord](
      mongoComponent,
      "deployment",
      DeploymentRecord.format,
      Seq.empty
    ) { underlying =>

  def get(formTemplateId: FormTemplateId): Future[List[DeploymentRecord]] =
    underlying.collection
      .find(equal("formTemplateId", formTemplateId.value))
      .sort(descending("createdAt"))
      .toFuture()
      .map(_.toList)

  def save(deploymentRecord: DeploymentRecord): Future[Unit] =
    collection.insertOne(deploymentRecord).toFuture().map(_ => ())
}
