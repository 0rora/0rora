package models

import java.time.ZonedDateTime

import akka.actor.{Actor, ActorSystem, Props}
import akka.stream.ActorMaterializer
import javax.inject.{Inject, Singleton}
import models.Payment.Validating
import models.PaymentProcessor.{UpdateNextPaymentTime, _}
import models.repo.PaymentRepo
import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment, Logger}
import scalikejdbc.{AutoSession, DBSession}
import stellar.sdk.model.response.{TransactionApproved, TransactionRejected}
import stellar.sdk.model.result.TransactionResult.{BadSequenceNumber, InsufficientBalance}
import stellar.sdk.model.result._
import stellar.sdk.model.{Account, Transaction}
import stellar.sdk.{Network, PublicKeyOps}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}
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
  system.scheduler.schedule(5.seconds, 5.seconds, actor, ValidateDuePayments)

  def checkForPayments(): Unit = actor ! UpdateNextPaymentTime
}

class PaymentProcessorActor(repo: PaymentRepo, accountCache: AccountCache, config: AppConfig) extends Actor {

  implicit private val materializer: ActorMaterializer = ActorMaterializer()
  implicit private val network: Network = config.network
  private implicit val ec: ExecutionContextExecutor = context.dispatcher

  private val logger: Logger = PaymentProcessor.logger
  override def receive: Receive = state(nextKnownPaymentDate = None)

  private def state(nextKnownPaymentDate: Option[ZonedDateTime]): Receive =
    validateDuePayments(nextKnownPaymentDate) orElse
      transactBatch orElse
      markPaymentsValid orElse
      markPaymentsInvalid orElse
      updateNextPaymentTime orElse
      confirmPayments orElse
      rejectPayments orElse
      rejectTransaction orElse
      retryPayments orElse
      updateAccount orElse
      registerAccount

//  val sink: Sink[(Seq[Payment], Account), NotUsed] = paymentSink(self, config)

//  val validateFlow: Flow[Payment, Either[InvalidatePayment, PaymentOperation], NotUsed] = {
//    def validate(p: Payment): Future[PaymentOperation] = for {
//      src <- p.source.pk()
//      dst <- p.destination.pk()
//    } yield PaymentOperation(dst, NativeAmount(p.units), Some(src))
//
//    Flow[Payment].mapAsyncUnordered(8) { p =>
//      val promise = Promise[Either[InvalidatePayment, PaymentOperation]]()
//      validate(p).onComplete {
//        case Success(po) => promise.complete(Success(Right(po)))
//        case Failure(t)  => promise.complete(Success(Left(InvalidatePayment(p, t))))
//      }
//      promise.future
//    }
//  }

//  val filterInvalidFlow: Flow[Either[InvalidatePayment, PaymentOperation], PaymentOperation, NotUsed] =
//    Flow[Either[InvalidatePayment, PaymentOperation]].mapConcat {
//      case Left(i) =>
//        self ! i
//        collection.immutable.Seq.empty[PaymentOperation]
//      case Right(op) =>
//        collection.immutable.Seq(op)
//    }

//  val batchWithAccountFLow: Flow[PaymentOperation, (Seq[PaymentOperation], Account), NotUsed] =
//    Flow[PaymentOperation]
//      .groupedWithin(100, 1 second)
//    .zip()


//  private def toPaymentOperation(p: Payment): Future[PaymentOperation] = for {
//    src <- p.source.pk()
//    dst <- p.destination.pk()
//  } yield PaymentOperation(dst, NativeAmount(p.units), Some(src))


  def validateDuePayments(nextKnownPaymentDate: Option[ZonedDateTime]): PartialFunction[Any, Unit] = {
    case ValidateDuePayments if nextKnownPaymentDate.exists(_.isBefore(ZonedDateTime.now())) =>
      println(ValidateDuePayments)
      repo.due(100) match {
        case Nil =>
          self ! UpdateNextPaymentTime
          self ! TransactBatch(0)
        case due =>
          logger.debug(s"Validating ${due.size} payments")
          repo.updateStatus(due.flatMap(_.id), Validating)
          val accountsToResolve = (due.map(_.source) ++ due.map(_.destination)).distinct
          Future.sequence(
            accountsToResolve.map(a => a.pk().map(Success(_)).recover{ case t => Failure(t) }.map(a -> _))
          ).map(_.toMap).map(_.flatMap {
            case (k, Success(pk)) => Some(k -> pk)
            case _ => None
          }).map { resolved =>
            val (failures, successes) = due.partition(p => resolved.get(p.source).isEmpty || resolved.get(p.destination).isEmpty)
            self ! MarkPaymentsInvalid(failures)
            self ! MarkPaymentsValid(successes, resolved)
          }
          self ! ValidateDuePayments
      }
  }

  val markPaymentsInvalid: PartialFunction[Any, Unit] = {
    case MarkPaymentsInvalid(ps: Seq[Payment]) if ps.nonEmpty => repo.invalidate(ps.flatMap(_.id), ZonedDateTime.now)
  }

  val markPaymentsValid: PartialFunction[Any, Unit] = {
    case MarkPaymentsValid(ps: Seq[Payment], resolved: Map[AccountIdLike, PublicKeyOps]) if ps.nonEmpty =>
      repo.validate(ps.flatMap(p => p.id.map(id => (id, resolved(p.source), resolved(p.destination)))))
  }

  val transactBatch: PartialFunction[Any, Unit] = {
    case TransactBatch(iter) =>
      accountCache.borrowAccount match {
        case Some(source) =>
          repo.valid(100) match {
            case Nil =>
              context.system.scheduler.scheduleOnce(1 second, self, TransactBatch(iter + 1))
              accountCache.returnAccount(source)
            case batch =>
              repo.submit(batch.flatMap(_.id), ZonedDateTime.now)
              if (batch.size == 100) { self ! TransactBatch(0) }
              val ops = batch.flatMap(_.asOperation)
              val h +: t = (source.publicKey +: ops.flatMap(_.sourceAccount))
                .map(_.accountId).distinct.flatMap(config.accounts.get)
              Transaction(source, ops).sign(h, t: _*).submit().onComplete {

                case Success(_: TransactionApproved) =>
                  logger.debug(s"[${source.publicKey.accountId}] Successful ${ops.size} payments")
                  self ! Confirm(batch, source)

                case Success(x: TransactionRejected) =>
                  x.result match {
                    case r@TransactionFailure(_, operationResults) =>
                      logger.debug(s"[${source.publicKey.accountId}] Failure  - ${x.detail} - $operationResults")
                      self ! RejectPayments(batch, operationResults, source, r.sequenceUpdated)

                    case r@TransactionNotAttempted(reason, _) =>
                      logger.debug(s"[${source.publicKey.accountId}] Not attempted because $reason")
                      reason match {
                        case InsufficientBalance =>
                          self ! RetryPayments(batch, source)
                        case BadSequenceNumber =>
                          self ! RetryPayments(batch, source)
                          self ! RegisterAccount(source.publicKey)
                        case _ =>
                          self ! RejectTransaction(batch, source, r.sequenceUpdated)
                      }
                  }

                case Failure(t) =>
                  ???
              }
          }

        case None if iter < 30 =>
          context.system.scheduler.scheduleOnce(1 second, self, TransactBatch(iter + 1))
        case None =>
          context.system.scheduler.scheduleOnce(1 minute, self, TransactBatch(iter + 1))
      }
  }


/*
  // If there are payments due, find and pay them
  def processPayments(nextKnownPaymentDate: Option[ZonedDateTime]): PartialFunction[Any, Unit] = {
    case ProcessPayments if nextKnownPaymentDate.exists(_.isBefore(ZonedDateTime.now())) =>
      accountCache.borrowAccount.foreach { source =>
        for {
          payments <- Future(repo.due(110))
          validated <- Future.sequence(payments.map { p =>
            val promise = Promise[Either[InvalidatePayment, (Payment, PaymentOperation)]]()
            toPaymentOperation(p).onComplete {
              case Success(po) => promise.complete(Success(Right(p -> po)))
              case Failure(t)  => promise.complete(Success(Left(InvalidatePayment(p, t))))
            }
            promise.future
          })
          (
            invalid: Seq[Either[InvalidatePayment, (Payment, PaymentOperation)]],
            valid: Seq[Either[InvalidatePayment, (Payment, PaymentOperation)]]
          ) <- validated.partition(_.isLeft)
          ops = valid.take(100).map { case Right(tpl) => tpl }
          _ <- Future(repo.invalidate(invalid.flatMap{ case Left(ip) => ip.payment.id }))
          _ <- Future(repo.submit(ops.flatMap(_._1.id), ZonedDateTime.now))
        } {
          val h +: t = (source.publicKey +: ops.flatMap(_._2.sourceAccount))
            .map(_.accountId).distinct.flatMap(config.accounts.get)
          Transaction(source, ops.map(_._2)).sign(h, t: _*).submit().onComplete {

            case Success(_: TransactionApproved) =>
              logger.debug(s"[${source.publicKey.accountId}] Successful ${ops.size} payments")
              self ! Confirm(ops.map(_._1), source)

            case Success(x: TransactionRejected) =>
              x.result match {
                case r@TransactionFailure(_, operationResults) =>
                  logger.debug(s"[${account.publicKey.accountId}] Failure  - ${x.detail} - $operationResults")
                  processor ! RejectPayments(ps, operationResults, account, r.sequenceUpdated)

                case r@TransactionNotAttempted(reason, _) =>
                  logger.debug(s"[${account.publicKey.accountId}] Not attempted because $reason")
                  reason match {
                    case InsufficientBalance =>
                      processor ! RetryPayments(ps, account)
                    case BadSequenceNumber =>
                      processor ! RetryPayments(ps, account)
                      processor ! RegisterAccount(account.publicKey)
                    case _ =>
                      processor ! RejectTransaction(ps, account, r.sequenceUpdated)
                  }
              }
          }
        }
      }





/*



        val partitioned = validated.map(_.partition(_.isLeft))

        partitioned.foreach { case (failed, _) =>
            failed.map { case Left()}
        }


        validated.map(_.map(_.left))





        if (payments.isEmpty) {
          logger.debug("No more payments due.")
          context.become(state(repo.earliestTimeDue))
        } else {
          payments



          val submittingPaymentsWithAccounts: Seq[(Seq[Payment], Account)] =
            payments.grouped(100).flatMap(ps => accountCache.borrowAccount.map(ps -> _)).toSeq
          val submittingPayments: Seq[Payment] = submittingPaymentsWithAccounts.flatMap(_._1)
          repo.submit(submittingPayments.flatMap(_.id), ZonedDateTime.now)
          Source.fromIterator(() => submittingPaymentsWithAccounts.iterator).to(sink).run()
        }
*/
  }
*/

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
      // val operationResults = if (opResults.forall(_ == PaymentSuccess)) opResults.map(_ => "OK") else // todo - is this still required?
      val operationResults = opResults.map {
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
  case object ValidateDuePayments extends Commands
  case object ProcessPayments extends Commands
  case object UpdateNextPaymentTime extends Commands
  case class RegisterAccount(publicKey: PublicKeyOps) extends Commands
  case class Confirm(payments: Seq[Payment], account: Account) extends Commands
  case class RejectPayments(payments: Seq[Payment], operationResults: Seq[OperationResult], account: Account, updatedSeqNo: Boolean) extends Commands
  case class RejectTransaction(payments: Seq[Payment], account: Account, updatedSeqNo: Boolean) extends Commands
  case class RetryPayments(payments: Seq[Payment], account: Account) extends Commands
  case class UpdateAccount(accn: Account) extends Commands
  case class InvalidatePayment(payment: Payment, cause: Throwable) extends Commands

  case class TransactBatch(iter: Int) extends Commands
  case class MarkPaymentsInvalid(ps: Seq[Payment]) extends Commands
  case class MarkPaymentsValid(ps: Seq[Payment], resolved: Map[AccountIdLike, PublicKeyOps]) extends Commands

  val logger: Logger = Logger("0rora.payment_processor")

/*
  def paymentSink(processor: ActorRef, config: AppConfig)(implicit ec: ExecutionContext):
  Sink[(Seq[Payment], Account), NotUsed] =
    Flow[(Seq[Payment], Account)]
      .map { case (ps, account) =>
        val operations: Future[Seq[PaymentOperation]] = Future.sequence(ps.map(_.asOperation))
        val paymentSigners: Future[Seq[PublicKeyOps]] = Future.sequence(ps.map(_.source.pk()))
        for {
          ops <- operations
          pss <- paymentSigners
          signers = (account.publicKey +: pss).map(_.accountId).distinct.flatMap(config.accounts.get)
          _ =  logger.debug(s"[${account.publicKey.accountId}] transacting (ops=${ops.size}, seqNo=${account.sequenceNumber})")
          txn = Transaction(account, ops)(config.network).sign(signers.head, signers.tail: _*)
          resp <- txn.submit().map(_ -> ps -> account)
        } yield resp
      }
      .mapAsync(parallelism = config.accounts.size)(_.map {
        case ((_: TransactionApproved, ps), account) =>
          logger.debug(s"[${account.publicKey.accountId}] Successful ${ps.size} payments")
          processor ! Confirm(ps, account)

        case ((x: TransactionRejected, ps), account) =>
          x.result match {
            case r@TransactionFailure(_, operationResults) =>
              logger.debug(s"[${account.publicKey.accountId}] Failure  - ${x.detail} - $operationResults")
              processor ! RejectPayments(ps, operationResults, account, r.sequenceUpdated)

            case r@TransactionNotAttempted(reason, _) =>
              logger.debug(s"[${account.publicKey.accountId}] Not attempted because $reason")
              reason match {
                case InsufficientBalance =>
                  processor ! RetryPayments(ps, account)
                case BadSequenceNumber =>
                  processor ! RetryPayments(ps, account)
                  processor ! RegisterAccount(account.publicKey)
                case _ =>
                  processor ! RejectTransaction(ps, account, r.sequenceUpdated)
              }
          }
      })
      .to(Sink.ignore)
*/

}