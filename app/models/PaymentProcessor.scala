package models

import java.time.ZonedDateTime

import akka.NotUsed
import akka.actor.{Actor, ActorSystem, Props}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import javax.inject.{Inject, Singleton}
import models.PaymentProcessor.{UpdateNextPaymentTime, _}
import models.repo.PaymentRepo
import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment, Logger}
import scalikejdbc.{AutoSession, DBSession}
import stellar.sdk.model.response.{TransactionApproved, TransactionRejected}
import stellar.sdk.model.result.TransactionResult.{BadSequenceNumber, InsufficientBalance}
import stellar.sdk.model.result._
import stellar.sdk.model.{Account, Transaction}
import stellar.sdk.{KeyPair, Network, PublicKeyOps}

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.util.{Failure, Success}

@Singleton
class PaymentProcessor @Inject()(repo: PaymentRepo,
                                 config: AppConfig,
                                 accountCache: AccountCache,
                                 system: ActorSystem) {

  private val actor = system.actorOf(Props(classOf[PaymentProcessorActor], repo, accountCache, config))
  private implicit val ec: ExecutionContextExecutor = system.dispatcher

  config.accounts.values.map(RegisterAccount).foreach(actor ! _)
  actor ! UpdateNextPaymentTime
  system.scheduler.schedule(5.seconds, 5.seconds, actor, ProcessPayments)

  def checkForPayments(): Unit = actor ! UpdateNextPaymentTime
}

class PaymentProcessorActor(repo: PaymentRepo, accountCache: AccountCache, config: AppConfig) extends Actor {

  implicit private val materializer: ActorMaterializer = ActorMaterializer()
  implicit private val network: Network = config.network
  private implicit val ec: ExecutionContextExecutor = context.dispatcher

  private val logger: Logger = Logger("0rora.payment_processor")

  val paymentSink: Sink[(Seq[Payment], Account), NotUsed] = Flow[(Seq[Payment], Account)]
    .map { case (ps, account) =>
      val operations = ps.map(_.asOperation)
      val signers: Seq[KeyPair] =
        (account.publicKey +: ps.map(_.source)).map(_.accountId).distinct.flatMap(config.accounts.get)
      logger.debug(s"[${account.publicKey.accountId}] transacting (ops=${operations.size}, seqNo=${account.sequenceNumber})")
      val txn = Transaction(account, operations).sign(signers.head, signers.tail: _*)
      txn.submit().map(_ -> ps -> account)
    }
    .mapAsync(parallelism = config.accounts.size)(_.map {
      case ((_: TransactionApproved, ps), account) =>
        logger.debug(s"[${account.publicKey.accountId}] Successful ${ps.size} payments")
        self ! Confirm(ps, account)

      case ((x: TransactionRejected, ps), account) =>
        x.result match {
          case r@TransactionFailure(_, operationResults) =>
            logger.debug(s"[${account.publicKey.accountId}] Failure  - ${x.detail} - $operationResults")
            self ! RejectPayments(ps, operationResults, account, r.sequenceUpdated)

          case r@TransactionNotAttempted(reason, _) =>
            logger.debug(s"[${account.publicKey.accountId}] Not attempted because $reason")
            reason match {
              case InsufficientBalance =>
                self ! RetryPayments(ps, account)
              case BadSequenceNumber =>
                self ! RetryPayments(ps, account)
                self ! RegisterAccount(account.publicKey)
              case _ =>
                self ! RejectTransaction(ps, account, r.sequenceUpdated)
            }
        }
    })
    .to(Sink.ignore)


  override def receive: Receive = state(nextKnownPaymentDate = None)

  private def state(nextKnownPaymentDate: Option[ZonedDateTime]): Receive =
    processPayments(nextKnownPaymentDate) orElse
      updateNextPaymentTime orElse
      confirmPayments orElse
      rejectPayments orElse
      rejectTransaction orElse
      retryPayments orElse
      updateAccount orElse
      registerAccount


  // If there are payments due, find and pay them
  def processPayments(nextKnownPaymentDate: Option[ZonedDateTime]): PartialFunction[Any, Unit] = {
    case ProcessPayments if nextKnownPaymentDate.exists(_.isBefore(ZonedDateTime.now())) =>
      val readyAccounts = accountCache.readyCount
      if (readyAccounts > 0) {
        val payments = repo.due(readyAccounts * 100)
        if (payments.isEmpty) {
          logger.debug("No more payments due.")
          context.become(state(repo.earliestTimeDue))
        } else {
          val submittingPaymentsWithAccounts: Seq[(Seq[Payment], Account)] =
            payments.grouped(100).flatMap(ps => accountCache.borrowAccount.map(ps -> _)).toSeq
          val submittingPayments: Seq[Payment] = submittingPaymentsWithAccounts.flatMap(_._1)
          repo.submit(submittingPayments.flatMap(_.id), ZonedDateTime.now)

          Source.fromIterator(() => submittingPaymentsWithAccounts.iterator).to(paymentSink).run()
        }
      }
  }

  // Check the database to find when the next pending payment is due
  val updateNextPaymentTime: PartialFunction[Any, Unit] = {
    case UpdateNextPaymentTime => context.become(state(repo.earliestTimeDue))
  }

  // Confirm that these payments have been successful and that this account is ready to use
  val confirmPayments: PartialFunction[Any, Unit] = {
    case Confirm(payments, account) =>
      repo.confirm(payments.flatMap(_.id))
      accountCache.returnAccount(account.withIncSeq)
  }

  // Mark these payments as failed and handle account
  val rejectPayments: PartialFunction[Any, Unit] = {
    case RejectPayments(payments, opResults, account, updatedSeqNo) =>
      val operationResults = if (opResults.forall(_ == PaymentSuccess)) opResults.map(_ => "OK") else
        opResults.map {
          case PaymentSuccess | CreateAccountSuccess => "Batch Failure"
          case x => x.getClass.getSimpleName.replaceAll("([a-z])([A-Z])", "$1 $2").replaceFirst("\\$$", "")
        }
      repo.rejectWithOpResult(payments.zip(operationResults).flatMap { case (p, r) => p.id.map(_ -> r) })
      val account_ = if (updatedSeqNo) account.withIncSeq else account
      accountCache.returnAccount(account_)
      self ! ProcessPayments
  }

  // Mark these payments as failed and handle account
  val rejectTransaction: PartialFunction[Any, Unit] = {
    case RejectTransaction(payments, account, updatedSeqNo) =>
      repo.reject(payments.flatMap(_.id))
      val account_ = if (updatedSeqNo) account.withIncSeq else account
      accountCache.returnAccount(account_)
      self ! ProcessPayments

  }

  val retryPayments: PartialFunction[Any, Unit] = {
    case RetryPayments(payments, account) =>
      repo.retry(payments.flatMap(_.id))
      accountCache.retireAccount(account)
      context.become(state(nextKnownPaymentDate = Some(ZonedDateTime.now())))
      self ! ProcessPayments
  }

  // Add a new account to the pool, or update the details
  val updateAccount: PartialFunction[Any, Unit] = {
    case UpdateAccount(accn) =>
      accountCache.returnAccount(accn)
  }

  // Fetch account details from the network, and trigger update.
  val registerAccount: PartialFunction[Any, Unit] = {
    case RegisterAccount(kp) =>
      logger.debug(s"[${kp.accountId}] details being obtained from Horizon.")
      network.account(kp).onComplete {
        case Success(accn) => self ! UpdateAccount(accn.toAccount)
        case Failure(t) => logger.warn(s"Unable to register account ${kp.accountId}", t)
      }
  }
}

class PaymentProcessorModule extends Module {
  def bindings(env: Environment, config: Configuration): Seq[Binding[_]] =
    Seq(
      bind[PaymentProcessor].toSelf.eagerly(),
      bind[DBSession].to(AutoSession)
    )
}

object PaymentProcessor {

  sealed trait Commands
  case object ProcessPayments extends Commands
  case object UpdateNextPaymentTime extends Commands
  case class RegisterAccount(publicKey: PublicKeyOps) extends Commands
  case class Confirm(payments: Seq[Payment], account: Account) extends Commands
  case class RejectPayments(payments: Seq[Payment], operationResults: Seq[OperationResult], account: Account, updatedSeqNo: Boolean) extends Commands
  case class RejectTransaction(payments: Seq[Payment], account: Account, updatedSeqNo: Boolean) extends Commands
  case class RetryPayments(payments: Seq[Payment], account: Account) extends Commands
  case class UpdateAccount(accn: Account) extends Commands

}