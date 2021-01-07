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

package uk.gov.hmrc.eeittadminfrontend

import play.api.mvc.AnyContentAsFormUrlEncoded

object TestUsers {

  def invalidUser() =
    AnyContentAsFormUrlEncoded(Map("token" -> Seq("invalid@test.com")))

  def registration() =
    AnyContentAsFormUrlEncoded(
      Map(
        "email.value"           -> Seq("test@test.com"),
        "permissions.[0].value" -> Seq("Query"),
        "permissions.[1].value" -> Seq("Deltas")))

  def validUser() =
    AnyContentAsFormUrlEncoded(Map("token" -> Seq("test@test.com")))

}
