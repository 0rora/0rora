package controllers.flows

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Sink}
import com.typesafe.config.Config
import play.api.Configuration
import stellar.sdk.{KeyPair, Network, TestNetwork, Transaction}
import stellar.sdk.op.PaymentOperation

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

case class PaymentFlow(config: Configuration) {

  implicit val network: Network = TestNetwork
  private val signerKey = KeyPair.fromSecretSeed(config.get[String]("luxe.account.secret"))
  private var account = Await.result(network.account(signerKey), 10.seconds)

  val sink: Sink[PaymentOperation, NotUsed] = Flow[PaymentOperation]
    .groupedWithin(100, 1.second)
    .map(Transaction(account, _))
    .map(_.sign(signerKey))
    .mapAsync(1)(_.submit())
    .to(Sink.foreach(println))

}
