package models

import java.time.ZonedDateTime

import akka.NotUsed
import akka.actor.{Actor, ActorSystem, Props}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import javax.inject.{Inject, Singleton}
import models.repo.{Payment, PaymentRepo}
import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment, Logger}
import stellar.sdk.model.response.{TransactionApproved, TransactionRejected}
import stellar.sdk.model.{Account, Transaction}
import stellar.sdk.{KeyPair, Network, TestNetwork}

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.util.{Failure, Success}

@Singleton
class PaymentProcessor @Inject()(repo: PaymentRepo,
                                 config: Configuration,
                                 system: ActorSystem) {

  private val actor = system.actorOf(Props(new ActorDef()))
  private val signerKey = KeyPair.fromSecretSeed(config.get[String]("luxe.account.secret"))
  private implicit val ec: ExecutionContextExecutor = system.dispatcher

  actor ! RegisterAccount(signerKey)
  actor ! UpdateNextPaymentTime
  system.scheduler.schedule(5.seconds, 5.seconds, actor, ProcessPayments)

  def checkForPayments(): Unit = actor ! UpdateNextPaymentTime

  class ActorDef() extends Actor {

    implicit private val materializer: ActorMaterializer = ActorMaterializer()
    implicit private val network: Network = TestNetwork

    val paymentSink: Sink[(Seq[Payment], Account), NotUsed] = Flow[(Seq[Payment], Account)]
      .map{ case (ps, account) =>
        val operations = ps.map(_.asOperation)
        Logger.debug(s"Transacting account ${account.publicKey} (seqNo ${account.sequenceNumber})")
        val txn = Transaction(account, operations).sign(signerKey)
        txn.submit().map(_ -> ps -> account)
      }
      .mapAsync(parallelism = 1)(_.map{
        case ((x: TransactionApproved, ps), account) =>
          Logger.debug(s"Successful")
          self ! Confirm(ps, account)
        case ((x: TransactionRejected, ps), account) =>
          Logger.debug(s"Failure ${x.resultCode} xx${x.resultXDR} $x")
          println(x)
          self ! Reject(ps, account)
      })
      .to(Sink.ignore)


    override def receive: Receive = state(
      readyAccounts = Nil,
      busyAccounts = Nil,
      nextKnownPaymentDate = None
    )

    private def state(
      readyAccounts: Seq[Account],
      busyAccounts: Seq[Account],
      nextKnownPaymentDate: Option[ZonedDateTime]): Receive = {

      // If there are payments due, find and pay them
      case ProcessPayments if nextKnownPaymentDate.exists(_.isBefore(ZonedDateTime.now())) =>
        if (readyAccounts.isEmpty) {
          Logger.debug(s"Payments are due, but no accounts are ready")
        } else {
          val payments = repo.due
          val submittingPayments = payments.take(100 * readyAccounts.size)
          val (submittingAccounts, leftOverAccounts) = readyAccounts.splitAt((submittingPayments.size / 100.0).ceil.toInt)
          Logger.debug(s"Processing ${submittingPayments.size}/${payments.size} pending payments via ${submittingAccounts.size} accounts (${submittingAccounts.map(_.sequenceNumber)}")

          repo.submit(submittingPayments.flatMap(_.id))
          Source.fromIterator(() => submittingPayments.iterator)
            .grouped(100)
            .zip(Source.fromIterator(() => submittingAccounts.iterator))
            .to(paymentSink).run()

          context.become(state(leftOverAccounts, submittingAccounts ++ busyAccounts, repo.earliestTimeDue))
        }

      // Check the database to find when the next pending payment is due
      case UpdateNextPaymentTime =>
        val due = repo.earliestTimeDue
        context.become(state(readyAccounts, busyAccounts, due))

      // Confirm that these payments have been successful and that this account is ready to use
      case Confirm(payments, account) =>
        repo.confirm(payments.flatMap(_.id))
        context.become(state(readyAccounts :+ account.withIncSeq, busyAccounts.filterNot(_ == account), nextKnownPaymentDate))

      // Mark these payments as failed and handle account
      case Reject(payments, account) =>
        repo.reject(payments.flatMap(_.id))
        context.become(state(readyAccounts :+ account.withIncSeq, busyAccounts.filterNot(_ == account), nextKnownPaymentDate))

      // Add a new account to the pool
      case UpdateAccount(accn) =>
        context.become(state(accn +: readyAccounts, busyAccounts, nextKnownPaymentDate))

      // Fetch details of account
      case RegisterAccount(kp) =>
        network.account(kp).onComplete{
          case Success(accn) => self ! UpdateAccount(accn.toAccount)
          case Failure(t) => Logger.warn(s"Unable to register account ${kp.accountId}", t)
        }

    }
  }

  private case object ProcessPayments
  private case object UpdateNextPaymentTime
  private case class RegisterAccount(keyPair: KeyPair)
  private case class Confirm(payments: Seq[Payment], account: Account)
  private case class Reject(payments: Seq[Payment], account: Account)
  private case class UpdateAccount(accn: Account)

}

class PaymentProcessorModule extends Module {
  def bindings(env: Environment, config: Configuration): Seq[Binding[_]] =
    Seq(bind(classOf[PaymentProcessor]).toSelf.eagerly())
}

