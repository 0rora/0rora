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
    .map{x => println(s"A! $x"); x}

    .map(_.sign(signerKey))
    .map{x => println(s"B! $x"); x}

    .map(_.submit())
    .map{x => println(s"C! $x"); x}

    .mapAsync(1)(_.recover { case t =>
      t.printStackTrace()
      null
    })

    .to(Sink.foreach(println))

}
