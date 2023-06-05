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

package uk.gov.hmrc.eeittadminfrontend.diff

import cats.syntax.eq._
import com.github.difflib.{ DiffUtils, UnifiedDiffUtils }
import com.github.difflib.patch.Patch

import scala.jdk.CollectionConverters._
import uk.gov.hmrc.eeittadminfrontend.deployment.{ ContentValue, Filename, GithubContent, MongoContent }

object DiffMaker {

  def inSync(mongo: MongoContent, github: GithubContent): Boolean = mongo.content === github.content

  def getDiff(
    originalFilename: String,
    revisedFilename: String,
    content1: ContentValue,
    content2: ContentValue
  ): String = {
    val json1Lines = content1.toLines.asJava
    val json2Lines = content2.toLines.asJava

    val patch: Patch[String] = DiffUtils.diff(json1Lines, json2Lines)

    UnifiedDiffUtils
      .generateUnifiedDiff(originalFilename, revisedFilename, json1Lines, patch, 5)
      .asScala
      .mkString("\\n")
      .replace("'", "\\'")
      .replace(
        "</script>",
        "＜/script>"
      ) // </script> in json causes html parser to end script block, we need to prevent that
  }

  def getDiff(filename: Filename, mongo: MongoContent, github: GithubContent): String =
    getDiff(filename.value, filename.value, mongo.content, github.content)

}
