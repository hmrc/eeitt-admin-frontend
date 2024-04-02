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

import cats.Eq

sealed trait GithubPath extends Product with Serializable
object GithubPath {
  case object HandlebarsPath extends GithubPath {
    val zipPath = "handlebars/"
  }

  case object HandlebarsSchemaPath extends GithubPath {
    val zipPath = "handlebarsSchemas/"
  }

  case object RootPath extends GithubPath {
    val zipPath = "formTemplates/"
  }

  def asPath(path: GithubPath): String = path match {
    case HandlebarsPath       => "handlebars/"
    case HandlebarsSchemaPath => "jsonSchemas/"
    case RootPath             => ""
  }

  def apply(path: String): GithubPath = path match {
    case _ if path.startsWith("handlebars/")  => HandlebarsPath
    case _ if path.startsWith("jsonSchemas/") => HandlebarsSchemaPath
    case _                                    => RootPath
  }
  implicit val equal: Eq[GithubPath] = Eq.fromUniversalEquals
}
