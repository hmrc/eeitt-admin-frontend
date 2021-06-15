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

package uk.gov.hmrc.eeittadminfrontend.diff

import com.github.difflib.{ DiffUtils, UnifiedDiffUtils }
import com.github.difflib.patch.Patch
import collection.JavaConverters._
import io.circe.Json
import uk.gov.hmrc.eeittadminfrontend.deployment.{ Filename, GithubContent, MongoContent }
import uk.gov.hmrc.eeittadminfrontend.models.github.PrettyPrintJson

object DiffMaker {

  private def toLines(json: Json): List[String] =
    PrettyPrintJson.asString(json).split("\n").toList

  def inSync(mongo: MongoContent, github: GithubContent): Boolean = {

    val json1Lines = toLines(mongo.json).asJava
    val json2Lines = toLines(github.json).asJava

    val patch: Patch[String] = DiffUtils.diff(json1Lines, json2Lines)
    patch.getDeltas().asScala.isEmpty

  }

  def getDiff(filename: Filename, mongo: MongoContent, github: GithubContent): String = {

    val json1Lines = toLines(mongo.json).asJava
    val json2Lines = toLines(github.json).asJava

    val patch: Patch[String] = DiffUtils.diff(json1Lines, json2Lines)

    UnifiedDiffUtils.generateUnifiedDiff(filename.value, filename.value, json1Lines, patch, 5).asScala.mkString("\\n")
  }

}
