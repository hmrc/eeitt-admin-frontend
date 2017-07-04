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

package uk.gov.hmrc.eeittadminfrontend.connectors

import play.api.libs.json.JsValue
import uk.gov.hmrc.eeittadminfrontend.WSHttp
import uk.gov.hmrc.eeittadminfrontend.models.FormTypeId
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.{ HeaderCarrier, HttpResponse }

import scala.concurrent.Future

object GformConnector {

  private val sc = new ServicesConfig {}
  val gformUrl = s"${sc.baseUrl("gform")}/gform"

  def getGformsTemplate(formTypeId: FormTypeId, version: String)(implicit hc: HeaderCarrier): Future[JsValue] = {
    WSHttp.GET[JsValue](gformUrl + s"/formtemplates/$formTypeId/$version")
  }

  def getAllGformsTemplates(implicit hc: HeaderCarrier): Future[JsValue] = {
    WSHttp.GET[JsValue](gformUrl + "/formtemplates")
  }

  def getAllSchema(implicit hc: HeaderCarrier): Future[JsValue] = {
    WSHttp.GET[JsValue](gformUrl + "/schemas")
  }

  def saveTemplate(gformTemplate: JsValue)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    WSHttp.POST[JsValue, HttpResponse](gformUrl + "/formtemplates", gformTemplate)
  }

}
