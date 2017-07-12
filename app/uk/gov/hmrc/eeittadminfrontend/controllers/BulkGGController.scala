package uk.gov.hmrc.eeittadminfrontend.controllers

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{Flow, GraphDSL, RunnableGraph, Sink, Source}
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import uk.gov.hmrc.eeittadminfrontend.AppConfig
import uk.gov.hmrc.eeittadminfrontend.config.Authentication
import uk.gov.hmrc.eeittadminfrontend.models.KnownFacts
import uk.gov.hmrc.play.frontend.auth.Actions
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

class BulkGGController(val authConnector: AuthConnector)(implicit appConfig: AppConfig, val messagesApi: MessagesApi) extends FrontendController with Actions with I18nSupport{


  def load(records: Seq[String]): Unit = {
    implicit val as = ActorSystem("GG")
    val mat = ActorMaterializer()

    val knownFactsLines: Iterator[String] = records.toIterator

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

      g.run()(mat)
  }

  val csvToKnownFact = Flow[String]
    .map(_.split(",").map(_.trim))
    .map(stringToKnownFacts)
    .throttle(1, 1.second, 1, ThrottleMode.shaping)

  def stringToKnownFacts(cols: Array[String]) = KnownFacts(cols(0), cols(1), cols(2))

  def averageSink[A](a: A) {
    a match {
      case KnownFacts(postCode, countryCode, utr) => Logger.info(s"Known fact ${postCode} ${countryCode} ${utr}")
      case x => println("no idea what " + x + "is!")
    }
  }
}