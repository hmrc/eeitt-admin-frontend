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

import akka.{ Done, NotUsed }
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{ Flow, GraphDSL, Keep, RunnableGraph, Sink, Source }
import play.api.Logger
import play.api.i18n.{ I18nSupport, MessagesApi }
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ Action, Result }
import uk.gov.hmrc.eeittadminfrontend.AppConfig
import uk.gov.hmrc.eeittadminfrontend.config.Authentication
import uk.gov.hmrc.eeittadminfrontend.connectors.EMACConnector
import uk.gov.hmrc.eeittadminfrontend.models._
import uk.gov.hmrc.play.frontend.auth.Actions
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.controller.FrontendController
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

class BulkGGController(val authConnector: AuthConnector, eMACConnector: EMACConnector)(implicit appConfig: AppConfig, val messagesApi: MessagesApi) extends FrontendController with Actions with I18nSupport {
  implicit val as = ActorSystem("GG")
  implicit val mat = ActorMaterializer.create(as)

  def load = Action.async(parse.urlFormEncoded) { implicit request =>
    val requestBuilder = request.body.apply("bulk-load").head
    Logger.info(s"$requestBuilder + //////////////")
    val knownFactsLines = Source.single(requestBuilder)

    lazy val csvToKnownFact = Flow[String]
      .map(_.split(",").map(_.trim))
      .map(stringToKnownFacts)
      .throttle(1, 1.second, 1, ThrottleMode.shaping)

    def stringToKnownFacts(cols: Array[String]) = BulkKnownFacts(Ref(Option(cols(0))), Utr(Option(cols(1))), Nino(Option(cols(2))), CountryCode(Option(cols(3))), PostCode(Option(cols(4))))

    def sink = Sink.fold[Future[List[JsValue]], BulkKnownFacts](Future.successful(List.empty[JsValue])) { (a, b) =>
      for {
        f1 <- averageSink(b)
        fseq <- a
      } yield f1 :: fseq
    }

    def averageSink(a: BulkKnownFacts): Future[JsValue] = {
      a match {
        case BulkKnownFacts(ref, utr, nino, postCode, countryCode) => {
          println("run/////////////////////")
          Logger.info(s"Known fact $ref $utr $nino $postCode $countryCode")
          eMACConnector.loadKF(a).map(x => x.get)
        }
      }
    }

    val runnable = knownFactsLines.via(csvToKnownFact).toMat(sink)(Keep.right)

    val res = runnable.run()

    for {
      a <- res
      b <- a
      c = b.head
    } yield Ok(c)

    /*    val g = RunnableGraph.fromGraph(GraphDSL.create() {
      implicit builder =>
        import GraphDSL.Implicits._

        //source
        val A: Outlet[String] = builder.add(Source.fromIterator(() => knownFactsLines)).out

        //flow
        val B: FlowShape[String, BulkKnownFacts] = builder.add(csvToKnownFact)

        //sink
        //        val C: Inlet[BulkKnownFacts] = builder.add(Sink.foreach(averageSink)).in
        //        val C: Inlet[BulkKnownFacts] = builder.add(sink).in
        val C = builder.add(sink)
        // val C: Inlet[BulkKnownFacts] = builder.add(averageSink).in

        //graph
        A ~> B ~> C

        ClosedShape

    })
    val done = g.run()*/

  }
}