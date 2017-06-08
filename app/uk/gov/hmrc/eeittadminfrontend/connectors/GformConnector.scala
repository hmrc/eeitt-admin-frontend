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



import play.api.libs.json.JsObject
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.eeittadminfrontend.WSHttp
import uk.gov.hmrc.eeittadminfrontend.models.FormTypeId
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet, HttpPost}

import scala.concurrent.{ExecutionContext, Future}

trait GformConnector {

  def httpGet: HttpGet = WSHttp
  def httpPost: HttpPost = WSHttp
  def gformUrl: String

def getGformsTemplate(formTypeId: FormTypeId, version: String)(implicit hc: HeaderCarrier): Future[Option[JsObject]] = {
  httpGet.GET[Option[JsObject]](gformUrl + s"/forms/$formTypeId/$version")
}
}

object GformConnector extends  GformConnector with ServicesConfig{
  lazy val HttpGet = WSHttp
  lazy val HttpPost = WSHttp

  override def gformUrl = "http://localhost:9196/gform"

}
