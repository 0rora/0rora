package controllers.flows

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Sink}
import stellar.sdk.op.PaymentOperation

import scala.concurrent.duration._

object PaymentFlow {

  val sink: Sink[PaymentOperation, NotUsed] = Flow[PaymentOperation]
    .groupedWithin(100, 1.second)
    .to(Sink.foreach(println))

}
