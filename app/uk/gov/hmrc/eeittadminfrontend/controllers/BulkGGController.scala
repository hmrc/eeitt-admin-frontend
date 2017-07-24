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

package uk.gov.hmrc.eeittadminfrontend.controllers

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{ Flow, Keep, Sink, Source }
import play.api.Logger
import play.api.http.Status
import play.api.i18n.{ I18nSupport, MessagesApi }
import play.api.libs.json.JsValue
import play.api.mvc.Action
import uk.gov.hmrc.eeittadminfrontend.connectors.EMACConnector
import uk.gov.hmrc.eeittadminfrontend.models._
import uk.gov.hmrc.play.frontend.auth.Actions
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.controller.FrontendController
import uk.gov.hmrc.play.http.{ HeaderCarrier, HttpResponse }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class BulkGGController(val authConnector: AuthConnector, eMACConnector: EMACConnector, val messagesApi: MessagesApi, actorSystem: ActorSystem, materializer: Materializer) extends FrontendController with Actions with I18nSupport {

  def load = Action.async(parse.urlFormEncoded) { implicit request =>
    val requestBuilder = request.body.apply("bulk-load").head
    Logger.info(s"Request content: $requestBuilder")
    stream(requestBuilder).map(x => Ok("Loaded"))
  }

  def stream(request: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val knownFactsLines = Source.single(request)

    lazy val csvToKnownFact = Flow[String]
      .map(_.split(",").map(_.trim))
      .map(stringToKnownFacts)
      .throttle(1, 3.second, 1, ThrottleMode.shaping)

    def stringToKnownFacts(cols: Array[String]) = BulkKnownFacts(Ref(cols(0)), Utr(Option(cols(1))), PostCode(Option(cols(3))), CountryCode(Option(cols(4))))

    def sink(implicit hc: HeaderCarrier) = Sink.fold[Future[List[HttpResponse]], BulkKnownFacts](Future.successful(List.empty[HttpResponse])) { (a, b) =>
      for {
        f1 <- averageSink(b)
        fseq <- a
      } yield f1 :: fseq
    }

    def averageSink(a: BulkKnownFacts)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
      a match {
        case BulkKnownFacts(ref, utr, postCode, countryCode) => {
          Logger.info(s"Known fact $ref $utr $postCode $countryCode")
          eMACConnector.loadKF(a)
        }
      }
    }

    val runnable = knownFactsLines.via(csvToKnownFact).toMat(sink)(Keep.right)

    val res = runnable.run()

    for {
      a <- res
      b <- a
      c = b.head
    } yield c

  }

  private implicit lazy val mat = materializer
  private implicit lazy val sys = actorSystem

}