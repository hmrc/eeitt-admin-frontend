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

//package howitworks
//
//import akka.actor.Actor.Receive
//import akka.actor._
//import akka.pattern._
//
//import scala.concurrent.duration._
//import scala.concurrent.{Await, Future}
//
//case class M(command: Comm, whatever: String)
//sealed trait Comm
//case object Do1 extends Comm
//case class Do2(i: Int) extends Comm
//
//class CounterActor extends Actor {
//
//  var c: Int = 0
//
//  override def receive: Receive = {
//    case M(Do1, "Not important") =>
//      print("Ok")
//
//  }
//}
//
//
//
//object HelloAkka extends App {
//
//  val system = ActorSystem()
//  implicit val t = akka.util.Timeout(10 seconds)
//
//  val cRef: ActorRef = system.actorOf(Props[CounterActor], "counter-actor")
//
//
//  cRef ! "Increment"
//  cRef ! "Increment"
//
//  val res: Future[Int] = (cRef ? "Get").mapTo[Int]
//
//  val c = Await.result(res, 15 seconds)
//  println(c)
//}
//
//
//import akka.stream._
//import akka.stream.scaladsl._
//import akka.{ NotUsed, Done }
//import akka.actor.ActorSystem
//import akka.util.ByteString
//import scala.concurrent._
//import scala.concurrent.duration._
//import java.nio.file.Paths
//
//object StreamsDemo extends App {
//  implicit val system = ActorSystem()
//  implicit val ec = system.dispatcher
//  implicit val materializer = ActorMaterializer()
//
//
//  val f: Flow[String, String, NotUsed] = Flow[String]
//
//  def lineSink(filename: String): Sink[String, Future[IOResult]] = {
//    val pathSink: Sink[ByteString, Future[IOResult]] = FileIO.toPath(Paths.get(filename))
//    Flow[String]
//      .map(s => ByteString(s + "\n"))
//      .toMat(pathSink)(Keep.right)
//  }
//
//
//}
