package models

import akka.NotUsed
import akka.actor.{Actor, ActorSystem, Props}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import javax.inject.{Inject, Singleton}
import models.repo.{Payment, PaymentRepo}
import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment, Logger}
import stellar.sdk.resp.{TransactionProcessed, TransactionRejected}
import stellar.sdk.{Account, KeyPair, Network, TestNetwork, Transaction}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor}

@Singleton
class PaymentProcessor @Inject()(repo: PaymentRepo,
                                 config: Configuration,
                                 system: ActorSystem) {

  private val actor = system.actorOf(Props(new ActorDef()))
  private implicit val ec: ExecutionContextExecutor = system.dispatcher

  def checkForPayments(reason: String, after: FiniteDuration = 1.second): Unit =
    system.scheduler.scheduleOnce(after, actor, CheckForPayments(reason))

  class ActorDef() extends Actor {

    implicit private val ec: ExecutionContextExecutor = context.dispatcher
    implicit private val materializer: ActorMaterializer = ActorMaterializer()
    implicit private val network: Network = TestNetwork

    private val signerKey = KeyPair.fromSecretSeed(config.get[String]("luxe.account.secret"))

    val paymentSink: Sink[(Seq[Payment], Account), NotUsed] = Flow[(Seq[Payment], Account)]
      .map{ case (ps, account) =>
        val operations = ps.map(_.asOperation)
        Logger.debug(s"Transacting account ${account.publicKey} (seqNo ${account.sequenceNumber})")
        val txn = Transaction(account, operations).sign(signerKey)
        Logger.debug(""+txn)
        txn.submit().map(_ -> ps -> account)
      }
      .mapAsync(parallelism = 1)(_.map{
        case ((x: TransactionProcessed, ps), account) =>
          Logger.debug(s"Successful ${x.resultXDR} $x")
          self ! Confirm(ps, account)
        case ((x: TransactionRejected, ps), account) =>
          Logger.debug(s"Failure ${x.resultXDR} $x")
          self ! Reject(ps, account)
        case x => // todo - see https://github.com/Synesso/scala-stellar-sdk/issues/49
      })
      .to(Sink.ignore)


    override def receive: Receive = transactUsing(Seq(Await.result(network.account(signerKey), 1.minute).toAccount), Nil)

    private def transactUsing(readyAccounts: Seq[Account], busyAccounts: Seq[Account]): Receive = {
      case CheckForPayments(reason) =>
        Logger.debug(s"Checking for due payments ($reason)")
        repo.due match {
          case Nil =>
            repo.durationUntilNextDue.filter(_ > Duration.Zero)
              .foreach(checkForPayments("Scheduled next payment", _))
          case _ if readyAccounts.isEmpty =>
            checkForPayments("No ready accounts at start of transact attempt", 5.seconds)
          case payments =>
            val (submittingPayments, leftOverPayments) = payments.splitAt(100 * readyAccounts.size)
            val (submittingAccounts, leftOverAccounts) = readyAccounts.splitAt((submittingPayments.size / 100.0).ceil.toInt)
            Logger.debug(s"Processing ${submittingPayments.size}/${payments.size} pending payments via ${submittingAccounts.size} accounts (${submittingAccounts.map(_.sequenceNumber)}")

            repo.submit(submittingPayments.flatMap(_.id))
            Source.fromIterator(() => submittingPayments.iterator)
              .grouped(100)
              .zip(Source.fromIterator(() => submittingAccounts.iterator))
              .to(paymentSink).run()

            if (leftOverPayments.nonEmpty) {
              self ! checkForPayments("No ready accounts remaining after batch transacting", 5.seconds)
            } else {
              repo.durationUntilNextDue.filter(_ > Duration.Zero)
                .foreach(checkForPayments("Scheduled next payment after prior transaction batch", _))
            }

            context.become(transactUsing(leftOverAccounts, busyAccounts ++ leftOverAccounts))
        }

      case Confirm(payments, account) =>
        repo.confirm(payments.flatMap(_.id))
        context.become(transactUsing(readyAccounts :+ account.withIncSeq, busyAccounts.filterNot(_ == account)))

      case Reject(payments, account) =>
        repo.reject(payments.flatMap(_.id))
        context.become(transactUsing(readyAccounts :+ account.withIncSeq, busyAccounts.filterNot(_ == account)))

    }
  }

  private case class CheckForPayments(reason: String)
  private case class Confirm(payments: Seq[Payment], account: Account)
  private case class Reject(payments: Seq[Payment], account: Account)

}

class PaymentProcessorModule extends Module {
  def bindings(env: Environment, config: Configuration): Seq[Binding[_]] =
    Seq(bind(classOf[PaymentProcessor]).toSelf.eagerly())
}

