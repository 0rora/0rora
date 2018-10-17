package models

import akka.NotUsed
import akka.actor.{Actor, ActorSystem, Props}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import javax.inject.{Inject, Singleton}
import models.repo.{Payment, PaymentRepo}
import play.api.{Configuration, Logger}
import stellar.sdk.resp.TransactionProcessed
import stellar.sdk.{KeyPair, Network, TestNetwork, Transaction}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor}

@Singleton
class PaymentProcessor @Inject()(repo: PaymentRepo,
                                 config: Configuration,
                                 system: ActorSystem) extends {

  private val actor = system.actorOf(Props(new ActorDef()))
  private implicit val ec: ExecutionContextExecutor = system.dispatcher

  def checkForPayments(after: FiniteDuration = 1.second): Unit =
    system.scheduler.scheduleOnce(after, actor, CheckForPayments)

  def pay(payments: Seq[Payment]): Unit = actor ! Pay(payments)

  class ActorDef() extends Actor {

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
        Logger.debug("Checking for due payments")
        val payments = repo.due
        if (payments.nonEmpty) {
          self ! Pay(payments)
        }

      case Pay(payments) =>
        Logger.debug(s"Paying ${payments.length} payments")
        repo.submit(payments.flatMap(_.id))
        Source.fromIterator(() => payments.iterator).to(paymentSink).run()
        repo.durationUntilNextDue.foreach(context.system.scheduler.scheduleOnce(_, self, CheckForPayments))

      case Confirm(payments) =>
        repo.confirm(payments.flatMap(_.id))

      case Reject(payments) =>
        repo.reject(payments.flatMap(_.id))

    }
  }

  private object CheckForPayments
  private case class Pay(payments: Seq[Payment])
  private case class Confirm(payments: Seq[Payment])
  private case class Reject(payments: Seq[Payment])

}



