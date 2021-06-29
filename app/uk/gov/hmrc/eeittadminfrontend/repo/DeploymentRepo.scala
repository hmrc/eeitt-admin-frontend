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

package uk.gov.hmrc.eeittadminfrontend.repo

import play.modules.reactivemongo.ReactiveMongoApi
import scala.concurrent.{ ExecutionContext, Future }
import reactivemongo.api.Cursor
import reactivemongo.api.bson.collection.BSONCollection
import reactivemongo.api.bson.BSONDocument
import reactivemongo.api.commands.WriteResult
import uk.gov.hmrc.eeittadminfrontend.models.FormTemplateId
import uk.gov.hmrc.eeittadminfrontend.deployment.DeploymentRecord

class DeploymentRepo(
  val reactiveMongoApi: ReactiveMongoApi
)(implicit ec: ExecutionContext) {

  private val collectionName: String = "deployment"

  private def collection: Future[BSONCollection] =
    reactiveMongoApi.database.map(_.collection[BSONCollection](collectionName))

  def get(formTemplateId: FormTemplateId): Future[List[DeploymentRecord]] =
    collection.flatMap(
      _.find(BSONDocument("formTemplateId" -> formTemplateId.value))
        .sort(BSONDocument("createdAt" -> -1))
        .cursor[DeploymentRecord]()
        .collect[List](-1, Cursor.FailOnError[List[DeploymentRecord]]())
    )

  def save(deploymentRecord: DeploymentRecord): Future[WriteResult] = collection.flatMap(_.insert.one(deploymentRecord))
}
