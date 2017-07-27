/*
 * Copyright 2017 HM Revenue & Customs
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

import play.api.libs.iteratee.Input.Empty
import play.api.libs.json.{ JsValue, Json }
import uk.gov.hmrc.eeittadminfrontend.connectors.KeyValuePair

case class BulkKnownFacts(
    ref: Ref,
    utr: Utr,
    postCode: PostCode,
    countryCode: CountryCode

) {

  override def toString = {

    val filteredList = List(utr.toString, postCode.toString, countryCode.toString).filter(_.nonEmpty)

    def getJson(list: List[String]): String = {

      if (list.size == 3) {
        s"""{"verifiers" : [${list.head},${list(1)},${list(2)}]}""".stripMargin
      } else if (list.size == 2) {
        s"""{"verifiers" : [${list.head},${list(1)}]}""".stripMargin
      } else
        s"""{"verifiers" : [${list.head}]}""".stripMargin
    }

    getJson(filteredList)
  }
}

case class Ref(ref: String) {
  override def toString = {
    ref.replaceAll("\\r\\n", "")
  }

}

case class Utr(utr: Option[String]) {
  override def toString = {
    utr match {
      case Some(utr) => s"""{"key" : "UTR","value" : "${utr}"}"""
      case None => ""
    }
  }
}

case class PostCode(postCode: Option[String]) {
  override def toString = {
    postCode match {
      case Some(y) if y.nonEmpty => s"""{"key" : "BusinessPostcode","value" : "${y}"}"""
      case _ => ""
    }
  }
}

case class CountryCode(countryCode: Option[String]) {
  override def toString = {
    countryCode match {
      case Some(x) if x.nonEmpty => s"""{"key" : "NonUKCountryCode","value" : "${x}"}"""
      case _ => ""
    }
  }
}