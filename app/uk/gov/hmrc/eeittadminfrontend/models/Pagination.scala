/*
 * Copyright 2022 HM Revenue & Customs
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

import cats.syntax.eq._
import cats.instances.int._

case class Pagination(count: Long, page: Int, submissionCount: Int) {
  val last: Int = Math.ceil(count.toDouble / Pagination.pageSize).toInt - 1
  val isFirstPage: Boolean = page === 0
  val isLastPage: Boolean = last <= page
  val previousPage: Int = if (isFirstPage) page else page - 1
  val nextPage: Int = if (isLastPage) page else page + 1
  val from: Int = if (submissionCount === 0) 0 else 1 + Pagination.pageSize * page
  val to: Long = if (submissionCount === 0) 0 else Math.min(Pagination.pageSize.toLong * (page + 1), count)
}

object Pagination {
  val pageSize: Int = 100
}
