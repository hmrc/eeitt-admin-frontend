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
import play.api.mvc.Action
import uk.gov.hmrc.eeittadminfrontend.AppConfig
import uk.gov.hmrc.eeittadminfrontend.config.Authentication
import uk.gov.hmrc.eeittadminfrontend.models.KnownFacts
import uk.gov.hmrc.play.frontend.auth.Actions
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.controller.FrontendController
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

class BulkGGController(val authConnector: AuthConnector)(implicit appConfig: AppConfig, val messagesApi: MessagesApi) extends FrontendController with Actions with I18nSupport {

  def load = Action.async { implicit request =>
    implicit val as = ActorSystem("GG")
    implicit val mat = ActorMaterializer.create(as)

    val knownFactsLines: Iterator[String] = request.body.asText.toIterator

    val g = RunnableGraph.fromGraph(GraphDSL.create() {
      implicit builder =>
        import GraphDSL.Implicits._

        //source
        val A: Outlet[String] = builder.add(Source.fromIterator(() => knownFactsLines)).out

        //flow
        val B: FlowShape[String, KnownFacts] = builder.add(csvToKnownFact)

        //sink
        val C: Inlet[Any] = builder.add(Sink.foreach(averageSink)).in
        //graph
        A ~> B ~> C

        ClosedShape

    })

    g.run()

    Future.successful(Ok("finished"))

  }

  val csvToKnownFact = Flow[String]
    .map(_.split(",").map(_.trim))
    .map(stringToKnownFacts)
    .throttle(1, 1.second, 1, ThrottleMode.shaping)

  def stringToKnownFacts(cols: Array[String]) = KnownFacts(cols(0), cols(1), cols(2))

  def averageSink[A](a: A)(implicit hc: HeaderCarrier) {
    a match {
      case KnownFacts(postCode, countryCode, utr) => Logger.info(s"Known fact ${postCode} ${countryCode} ${utr}")
      case x => println("no idea what " + x + "is!")
    }
  }
}