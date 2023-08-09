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

import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, ExecutionContext, Future, TimeoutException }

object DiffMaker {

  def inSync(mongo: MongoContent, github: GithubContent): Boolean = mongo.content === github.content

  def getDiff(
    originalFilename: String,
    revisedFilename: String,
    content1: ContentValue,
    content2: ContentValue,
    timeout: Duration
  )(implicit ec: ExecutionContext): String = {
    val json1Lines = content1.toLines.asJava
    val json2Lines = content2.toLines.asJava

    val patchF: Future[Patch[String]] = Future {
      DiffUtils.diff(json1Lines, json2Lines)
    }

    try {
      val patch: Patch[String] = Await.result(patchF, timeout)

      UnifiedDiffUtils
        .generateUnifiedDiff(originalFilename, revisedFilename, json1Lines, patch, 5)
        .asScala
        .mkString("\\n")
        .replace("'", "\\'")
        .replace(
          "</script>",
          "＜/script>"
        ) // </script> in json causes html parser to end script block, we need to prevent that
    } catch {
      case _: TimeoutException =>
        s"--- $originalFilename\\n+++ $originalFilename\\n@@  Too many changes to display  @@"
    } // Wait for the result with a timeout
  }

  def getDiff(filename: Filename, mongo: MongoContent, github: GithubContent, timeout: Duration)(implicit
    ec: ExecutionContext
  ): String =
    getDiff(filename.value, filename.value, mongo.content, github.content, timeout)

}
