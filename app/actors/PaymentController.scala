package actors

import actors.PaymentController._
import actors.PaymentRepository.UpdateStatus
import akka.actor.{Actor, ActorRef}
import models.Payment.Failed
import models.{AppConfig, Payment}
import play.api.Logger
import stellar.sdk.model.response.{TransactionApproved, TransactionPostResponse, TransactionRejected}
import stellar.sdk.model.result.TransactionResult.{BadSequenceNumber, InsufficientBalance}
import stellar.sdk.model.result.{PaymentResult, PaymentSuccess, TransactionFailure, TransactionNotAttempted}
import stellar.sdk.model.{Account, Transaction}
import stellar.sdk.{Network, PublicKeyOps}

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

class PaymentController(payRepo: ActorRef, accountRepo: ActorRef, config: AppConfig) extends Actor {

  import context.dispatcher

  implicit val network: Network = config.network

  override def preStart(): Unit = {
    payRepo ! Subscribe(self)
    accountRepo ! Subscribe(self)
    config.accounts.map(_._2.asPublicKey).foreach(accountRepo ! _)
  }

  override def receive: Receive = newState(State())

  private def newState(s: State): Receive = {
    pendingPayment(s) orElse validPayment(s) orElse invalidPayment(s) orElse
      payBatch(s) orElse flush(s) orElse updateAccount(s) orElse streamProgress(s)
  }

  // A new payment has arrived. Validate it.
  def pendingPayment(s: State): PartialFunction[Any, Unit] = {
    case p: Payment =>
      logger.trace(s"[payment ${p.id.get}] is pending")
      context.become(newState(s.incValidating))
      PaymentController.validate(p)(context.dispatcher, config) onComplete {
        case Success(valid) =>
          logger.trace(s"[payment ${p.id.get}] is valid")
          self ! Valid(valid)
        case Failure(t) =>
          logger.debug(s"[payment ${p.id.get}] is invalid: ${t.getMessage}")
          payRepo ! Invalid(p)
          self ! Invalid(p)
      }
  }

  def invalidPayment(s: State): PartialFunction[Any, Unit] = {
    case Invalid(_) =>
      if (s.validationsInFlight == 1 && s.streamsInProgress == 0) self ! FlushBatch
      context.become(newState(s.decValidating))
  }

  // A payment has been validated. Add it to the state and issue batches if necessary.
  def validPayment(s: State): PartialFunction[Any, Unit] = {
    case Valid(p) =>
      if (s.validationsInFlight == 1 && s.streamsInProgress == 0) self ! FlushBatch
      val (s_, batches) = s.addPending(p)
      batches.foreach(self ! _)
      context.become(newState(s_))
  }

  // Any pending payments are ready to batched
  def flush(s: State): PartialFunction[Any, Unit] = {
    case FlushBatch =>
      logger.debug("Flushing batches")
      val (s_, batches) = s.flush
      batches.foreach(self ! _)
      context.become(newState(s_))
  }

  // A payment batch is ready. Transact it.
  def payBatch(s: State): PartialFunction[Any, Unit] = {
    case b: PaymentBatch =>
      logger.debug(s"[batch ${s.batchId}] Submitting ${b.ps.size} payments for processing from ${b.accn.publicKey.accountId}")
      context.become(newState(s.withIncBatchId))
      transact(b).onComplete(handlePaymentResponse(b, s.batchId))
  }

  def handlePaymentResponse(b: PaymentBatch, id: Long): Try[TransactionPostResponse] => Unit = {
    case Success(_: TransactionApproved) =>
      logger.debug(s"[batch $id] Succeeded")
      payRepo ! UpdateStatus(b.ps, Payment.Succeeded)
      self ! UpdateAccount(b.accn.withIncSeq)

    case Success(x: TransactionRejected) =>
      x.result match {

        case TransactionFailure(_, operationResults) =>
          logger.debug(s"[batch $id] Attempted, but failed. ${x.detail}: $operationResults")
          self ! UpdateAccount(b.accn.withIncSeq)
          val paymentsByResult = operationResults.zip(b.ps).groupBy(_._1).mapValues(_.map(_._2))
          self ! StreamInProgress(true)
          paymentsByResult.foreach {
            // otherwise successful payments that were prevented from transacting. Try them again.
            case (PaymentSuccess, ps) => ps.foreach(self ! _)

            // payments that failed in their own right. Fail them in repo.
            case (r: PaymentResult, ps) => payRepo ! UpdateStatus(ps, Failed, Some(name(r)))

            // payments for which we have the default op result code, i.e. they weren't even assessed. Try them again.
            case (_, ps) => ps.foreach(self ! _)
          }
          self ! StreamInProgress(false)

        case TransactionNotAttempted(reason, _) =>
          logger.debug(s"[batch $id] Rejected because $reason")
          reason match {
            case InsufficientBalance =>
              // the source account did not have enough funds to pay the fee. Keep it out of circulation and retry payments.
              b.ps.foreach(self ! _)
            case BadSequenceNumber =>
              // the source account became out of sync. Retry the payments and refresh the account.
              b.ps.foreach(self ! _)
              accountRepo ! b.accn.publicKey
            case r =>
              // any other reason is an unexpected error. Fail the payments and log. Refresh the account for good measure.
              logger.error(s"Transaction was not attempted for unexpected reason: $r")
              payRepo ! UpdateStatus(b.ps, Failed, Some(name(r)))
              accountRepo ! b.accn.publicKey
          }
      }

    case Failure(t) =>
      // the comms with the network failed. Log it. Refresh account retry payments and after a delay.
      logger.error(s"[batch $id] Transaction POST failed", t)
      accountRepo ! b.accn.publicKey
      context.system.scheduler.scheduleOnce(1.minute) {
        b.ps.foreach(self ! _)
      }
  }

  // Account details have been updated
  def updateAccount(s: State): PartialFunction[Any, Unit] = {
    case UpdateAccount(accn) =>
      logger.debug(s"[account ${accn.publicKey.accountId}] Updated - seq no ${accn.sequenceNumber}")
      if (s.validationsInFlight == 0 && s.streamsInProgress == 0 && s.valid.nonEmpty) self ! FlushBatch
      context.become(newState(s.returnAccount(accn)))
  }

  def streamProgress(s: State): PartialFunction[Any, Unit] = {
    case StreamInProgress(in) =>
      val incOrDec = if (in) 1 else -1
      val streamsInProgress = s.streamsInProgress + incOrDec
      if (streamsInProgress == 0 && s.validationsInFlight == 0) self ! FlushBatch
      context.become(newState(s.copy(streamsInProgress = streamsInProgress)))
  }

  def transact(b: PaymentBatch): Future[TransactionPostResponse] = {
    val PaymentBatch(payments, source) = b
    val ops = payments.flatMap(_.asOperation)
    val accountIds = (source.publicKey +: ops.flatMap(_.sourceAccount)).map(_.accountId).distinct
    val h +: t = accountIds.map(config.accounts)
    Transaction(source, ops).sign(h, t: _*).submit()
  }

  def name(a: Any): String = a.getClass.getSimpleName.replace("$", "")

}


object PaymentController {

  val logger: Logger = Logger("0rora.payment_controller")

  case class Subscribe(subscriber: ActorRef)

  case class PaymentBatch(ps: Seq[Payment], accn: Account)

  case object FlushBatch

  case class Valid(p: Payment)

  case class Invalid(p: Payment)

  case class UpdateAccount(accn: Account)

  case class StreamInProgress(inProgress: Boolean)

  def validate(p: Payment)(implicit ec: ExecutionContext, config: AppConfig): Future[Payment] = for {
    s <- p.source.pk()
    d <- p.destination.pk()
  } yield {
    if (config.accounts.contains(s.accountId)) p.copy(sourceResolved = Some(s), destinationResolved = Some(d))
    else throw MissingSignerException(s)
  }

  case class State(valid: Seq[Payment] = Nil,
                   accounts: Map[String, Account] = Map.empty,
                   batchId: Long = 0,
                   validationsInFlight: Int = 0,
                   streamsInProgress: Int = 0) {

    def incValidating: State = copy(validationsInFlight = validationsInFlight + 1)
    def decValidating: State = copy(validationsInFlight = validationsInFlight - 1)

    def addPending(p: Payment): (State, Seq[PaymentBatch]) = {
      val valid_ = p +: valid
      if (valid_.size >= 100 && accounts.nonEmpty) {
        val (batches, remainingPayments, remainingAccounts) = group(valid_.reverse, accounts.values.toSeq, fullBatchesOnly = true)
        val accountsMap = remainingAccounts.map(a => a.publicKey.accountId -> a).toMap
        copy(valid = remainingPayments, accounts = accountsMap).decValidating -> batches
      } else {
        copy(valid = valid_).decValidating -> Nil
      }
    }

    def flush: (State, Seq[PaymentBatch]) = {
      val (batches, remainingPayments, remainingAccounts) = group(valid.reverse, accounts.values.toSeq, fullBatchesOnly = false)
      val accountsMap = remainingAccounts.map(a => a.publicKey.accountId -> a).toMap
      copy(valid = remainingPayments, accounts = accountsMap) -> batches
    }

    @tailrec
    private def group(ps: Seq[Payment], accns: Seq[Account], fullBatchesOnly: Boolean, batches: Seq[PaymentBatch] = Nil):
    (Seq[PaymentBatch], Seq[Payment], Seq[Account]) = {

      if (accns.isEmpty || ps.isEmpty) (batches, ps, accns)
      else if (ps.size < 100 && fullBatchesOnly) (batches, ps, accns)
      else group(ps.drop(100), accns.tail, fullBatchesOnly, PaymentBatch(ps.take(100), accns.head) +: batches)
    }

    def returnAccount(accn: Account): State = copy(accounts = accounts.updated(accn.publicKey.accountId, accn))

    def withIncBatchId: State = copy(batchId = batchId + 1)
  }

}

case class MissingSignerException(pk: PublicKeyOps) extends Exception(
  s"Missing signers for account ${pk.accountId}"
)