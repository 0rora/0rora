package controllers.flows

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import stellar.sdk.op.PaymentOperation
import stellar.sdk.resp.TransactionPostResp

object PaymentFlow {

//  implicit val system: ActorSystem = ActorSystem("payment-flow")
//  implicit val mat: ActorMaterializer = ActorMaterializer()

  val sink: Sink[PaymentOperation, NotUsed] = Flow[PaymentOperation].to(Sink.foreach(println))

}
