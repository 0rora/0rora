package models

import akka.NotUsed
import akka.actor.Actor
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import models.repo.{Payment, PaymentRepo}
import play.api.Configuration
import stellar.sdk.resp.TransactionProcessed
import stellar.sdk.{KeyPair, Network, TestNetwork, Transaction}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor}

class PaymentProcessor(repo: PaymentRepo, config: Configuration) extends Actor {

  implicit private val ec: ExecutionContextExecutor = context.dispatcher
  implicit private val materializer: ActorMaterializer = ActorMaterializer()
  implicit private val network: Network = TestNetwork

  private val signerKey = KeyPair.fromSecretSeed(config.get[String]("luxe.account.secret"))
  private val account = Await.result(network.account(signerKey), 10.seconds)

  val paymentSink: Sink[Payment, NotUsed] = Flow[Payment]
    .groupedWithin(100, 1.second)
    .map(ps => Transaction(account, ps.map(_.asOperation)).sign(signerKey).submit().map(_ -> ps))
    .mapAsync(parallelism = 1)(_.map{
      case (_: TransactionProcessed, ps) => self ! Confirm(ps)
      case (_, ps) => self ! Reject(ps)
    })
    .to(Sink.ignore)


  override def receive: Receive = {

    case CheckForPayments =>
      val payments = repo.due
      if (payments.nonEmpty) {
        repo.submit(payments.flatMap(_.id))
        self ! Pay(payments)
      }
      repo.durationUntilNextDue.foreach(context.system.scheduler.scheduleOnce(_, self, CheckForPayments))

    case Pay(payments) =>
      Source.fromIterator(() => payments.iterator).to(paymentSink).run()

    case Confirm(payments) =>
      repo.confirm(payments.flatMap(_.id))

    case Reject(payments) =>
      repo.reject(payments.flatMap(_.id))

  }
}

object CheckForPayments
case class Pay(payments: Seq[Payment])
case class Confirm(payments: Seq[Payment])
case class Reject(payments: Seq[Payment])