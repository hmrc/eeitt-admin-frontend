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

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{ Keep, Sink, Source }
import play.api.Logger
import play.api.i18n.{ I18nSupport, MessagesApi }
import play.api.mvc.Action
import uk.gov.hmrc.eeittadminfrontend.AppConfig
import uk.gov.hmrc.eeittadminfrontend.connectors.EMACConnector
import uk.gov.hmrc.eeittadminfrontend.models._
import uk.gov.hmrc.play.frontend.auth.Actions
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.controller.FrontendController
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

object BulkLoadHelper {
  def stringToKnownFacts(cols: Array[Option[String]]) = {
    BulkKnownFacts(Ref(cols(1).getOrElse("")), PostCode(Some(cols(10).getOrElse[String](""))), CountryCode(Some(cols(11).getOrElse[String](""))))
  }
}

class BulkGGController(val authConnector: AuthConnector, eMACConnector: EMACConnector, val messagesApi: MessagesApi, actorSystem: ActorSystem, materializer: Materializer)(implicit appConfig: AppConfig) extends FrontendController with Actions with I18nSupport {

  def load = Action.async(parse.urlFormEncoded) { implicit request =>
    val requestBuilder = request.body.apply("bulk-load").head.replace("select", " ").split(",").map {
      case " " => None
      case x => Some(x)
    }

    val knownFactAsString: Iterator[Array[Option[String]]] = requestBuilder.sliding(12, 12)
    val kf: List[BulkKnownFacts] = knownFactAsString.map(x => BulkLoadHelper.stringToKnownFacts(x)).toList

    stream(kf)

    Future.successful(Ok("Please look at the logs for updates on this bulk load"))
  }

  def stream(request: List[BulkKnownFacts])(implicit hc: HeaderCarrier) = {
    val knownFactsLines: Source[BulkKnownFacts, NotUsed] = Source.fromIterator(() => request.toIterator)

    def sink(implicit hc: HeaderCarrier) = Sink.fold[Future[List[Int]], BulkKnownFacts](Future.successful(List.empty[Int])) { (a, bulkKnownFact) =>

      for {
        responseStatus <- averageSink(bulkKnownFact)
        fseq <- a
      } yield {
        responseStatus :: fseq
      }
    }

    def averageSink(a: BulkKnownFacts)(implicit hc: HeaderCarrier): Future[Int] = {
      eMACConnector.loadKF(a).map {
        case 204 => 204
        case _ => {
          Logger.info("Their has been an error")
          actorSystem.terminate()
          500
        }
      }
    }

    val runnable = knownFactsLines
      .throttle(1, 3.second, 1, ThrottleMode.shaping)
      .toMat(sink)(Keep.right)

    val res: Future[Future[List[Int]]] = runnable.run()(mat)
  }
  private implicit lazy val sys = ActorSystem()
  private implicit lazy val mat = ActorMaterializer.create(ActorMaterializerSettings.create(sys)
    .withSyncProcessingLimit(1), sys)

}