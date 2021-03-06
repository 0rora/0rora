package actors

import actors.PaymentController._
import actors.PaymentRepository.UpdateStatus
import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import models.Generators.{genScheduledPayment, sampleOf}
import models.Payment.{Failed, Succeeded}
import models.db.AccountDao
import models.{AppConfig, RawAccountId, StubNetwork}
import org.mockito.Mockito.when
import org.scalacheck.Gen
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import org.scalatest.time.SpanSugar
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import stellar.sdk.model.op.PaymentOperation
import stellar.sdk.model.response.{TransactionApproved, TransactionPostResponse, TransactionRejected}
import stellar.sdk.model.result.TransactionResult.{BadSequenceNumber, InsufficientBalance, UnusedSignatures}
import stellar.sdk.model.result._
import stellar.sdk.model.{Account, NativeAmount}
import stellar.sdk.util.ByteArrays
import stellar.sdk.{InvalidAccountId, KeyPair, Network}

import scala.concurrent.Await

class PaymentControllerSpec extends TestKit(ActorSystem("payment-controller-spec")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with MockitoSugar with Eventually with SpanSugar {

  override def afterAll: Unit = TestKit.shutdownActorSystem(system)

  private val sampleAccount = Account(KeyPair.random, 1)

  "the payment controller" must {
    "subscribe to helpers and register configured accounts on startup" in {
      val (payRepoProbe, accnRepoProbe, config, accnRepo) = setup()

      val actor = system.actorOf(Props(new PaymentController(payRepoProbe.ref, accnRepoProbe.ref, config, accnRepo)))

      payRepoProbe.expectMsg(3.seconds, Subscribe(actor))
      accnRepoProbe.expectMsg(3.seconds, Subscribe(actor))
      accnRepoProbe.expectMsg(3.seconds, accnRepo.list.head.asPublicKey)
    }
  }

  "a pending payment" must {
    "be processed in a batch when valid" in {
      val (payRepoProbe, accnRepoProbe, config, accnRepo) = setup()
      val payment = sampleOf(genScheduledPayment).copy(source = RawAccountId(accnRepo.list.head.accountId))
      val accn = Account(accnRepo.list.head.asPublicKey, 123L)

      val actor = system.actorOf(Props(new PaymentController(payRepoProbe.ref, accnRepoProbe.ref, config, accnRepo)))
      payRepoProbe.expectMsg(3.seconds, Subscribe(actor))
      accnRepoProbe.expectMsg(3.seconds, Subscribe(actor))
      accnRepoProbe.expectMsg(3.seconds, accnRepo.list.head.asPublicKey)
      actor ! UpdateAccount(accn)

      actor ! StreamInProgress(true)
      actor ! payment
      actor ! StreamInProgress(false)

      eventually(timeout(3.seconds)) {
        val posted = config.network.asInstanceOf[StubNetwork].posted
        assert(posted.size == 1)
        assert(posted.head.transaction.operations == Seq(PaymentOperation(
          destinationAccount = KeyPair.fromAccountId(payment.destination.account),
          amount = NativeAmount(payment.units),
          sourceAccount = Some(accnRepo.list.head.asPublicKey)
        )))
      }
    }

    "be sent to the payment repo when invalid" in {
      val (payRepoProbe, accnRepoProbe, config, accnRepo) = setup()
      val payment = sampleOf(genScheduledPayment).copy(source = RawAccountId("invalid"))
      val accn = Account(accnRepo.list.head.asPublicKey, 123L)

      val actor = system.actorOf(Props(new PaymentController(payRepoProbe.ref, accnRepoProbe.ref, config, accnRepo)))
      payRepoProbe.expectMsg(3.seconds, Subscribe(actor))
      accnRepoProbe.expectMsg(3.seconds, Subscribe(actor))
      accnRepoProbe.expectMsg(3.seconds, accnRepo.list.head.asPublicKey)
      actor ! UpdateAccount(accn)

      actor ! StreamInProgress(true)
      actor ! payment
      actor ! StreamInProgress(false)

      payRepoProbe.expectMsg(Invalid(payment))
    }
  }

  "many pending payments" must {
    "be processed in several batches when valid" in {
      val (payRepoProbe, accnRepoProbe, config, accnRepo) = setup(numAccounts = 2)
      val payments = sampleOf(Gen.listOfN(120, genScheduledPayment)).map(_.copy(source = RawAccountId(accnRepo.list.head.accountId)))
      val actor = system.actorOf(Props(new PaymentController(payRepoProbe.ref, accnRepoProbe.ref, config, accnRepo)))
      payRepoProbe.expectMsg(3.seconds, Subscribe(actor))
      accnRepoProbe.expectMsg(3.seconds, Subscribe(actor))
      accnRepoProbe.expectMsg(3.seconds, accnRepo.list.head.asPublicKey)
      accnRepo.list.map(_.asPublicKey).map(Account(_, 123L)).foreach(actor ! UpdateAccount(_))

      actor ! StreamInProgress(true)
      payments.foreach(actor ! _)
      actor ! StreamInProgress(false)

      eventually(timeout(3.seconds)) {
        val posted = config.network.asInstanceOf[StubNetwork].posted
        assert(posted.map(_.transaction.operations.size) == Seq(100, 20))
      }
    }

    "be processed in time when there are insufficient accounts" in {
      val (payRepoProbe, accnRepoProbe, config, accnRepo) = setup()
      val payments = sampleOf(Gen.listOfN(120, genScheduledPayment)).map(_.copy(source = RawAccountId(accnRepo.list.head.accountId)))
      val actor = system.actorOf(Props(new PaymentController(payRepoProbe.ref, accnRepoProbe.ref, config, accnRepo)))
      payRepoProbe.expectMsg(3.seconds, Subscribe(actor))
      accnRepoProbe.expectMsg(3.seconds, Subscribe(actor))
      accnRepoProbe.expectMsg(3.seconds, accnRepo.list.head.asPublicKey)
      actor ! UpdateAccount(Account(accnRepo.list.head.asPublicKey, 123L))

      actor ! StreamInProgress(true)
      payments.foreach(actor ! _)
      actor ! StreamInProgress(false)

      eventually(timeout(3.seconds)) {
        val posted = config.network.asInstanceOf[StubNetwork].posted
        assert(posted.map(_.transaction.operations.size) == Seq(100, 20))
      }
    }
  }

  "validation of a payment" must {
    "succeed when source and destination are resolvable and source is known" in {
      val (_, _, config, accnRepo) = setup()
      val state = State(keyPairs = accnRepo.list.map(kp => kp.accountId -> kp).toMap)
      val payment = sampleOf(genScheduledPayment).copy(source = RawAccountId(accnRepo.list.head.accountId))
      assert(Await.result(PaymentController.validate(payment, state)(system.dispatcher, config), 1.second) ==
        payment.copy(
          sourceResolved = Some(KeyPair.fromAccountId(payment.source.account)),
          destinationResolved = Some(KeyPair.fromAccountId(payment.destination.account))
        )
      )
    }

    "fail when source is resolvable but is not known" in {
      val (_, _, config, accnRepo) = setup()
      val state = State(keyPairs = accnRepo.list.map(kp => kp.accountId -> kp).toMap)
      val payment = sampleOf(genScheduledPayment)
      assertThrows[MissingSignerException] {
        Await.result(PaymentController.validate(payment, state)(system.dispatcher, config), 1.second)
      }
    }

    "fail when source is not resolvable" in {
      val (_, _, config, accnRepo) = setup()
      val state = State(keyPairs = accnRepo.list.map(kp => kp.accountId -> kp).toMap)
      val payment = sampleOf(genScheduledPayment).copy(source = RawAccountId("invalidness"))
      assertThrows[InvalidAccountId] {
        Await.result(PaymentController.validate(payment, state)(system.dispatcher, config), 1.second)
      }
    }

    "fail when destination is not resolvable" in {
      val (_, _, config, accnRepo) = setup()
      val state = State(keyPairs = accnRepo.list.map(kp => kp.accountId -> kp).toMap)
      val payment = sampleOf(genScheduledPayment).copy(destination = RawAccountId("invalidness"))
      assertThrows[InvalidAccountId] {
        Await.result(PaymentController.validate(payment, state)(system.dispatcher, config), 1.second)
      }
    }
  }

  "handling transaction failure" must {
    "handle each payment according to its response" in {
      val (payRepoProbe, accnRepoProbe, config, accountRepo) = setup(
        respondWith = Seq(
          TransactionRejected(1, "", "", Nil, "",
            ByteArrays.base64(TransactionFailure(NativeAmount(100),
              Seq(PaymentSuccess, PaymentSuccess, PaymentUnderfunded, CreateAccountSuccess, CreateAccountSuccess)
            ).encode)),
          TransactionApproved("", 1, "", "", "")
        )
      )
      val payments = sampleOf(Gen.listOfN(5, genScheduledPayment)).map(_.copy(
        source = RawAccountId(accountRepo.list.head.accountId)
      ))

      val actor = system.actorOf(Props(new PaymentController(payRepoProbe.ref, accnRepoProbe.ref, config, accountRepo)))
      payRepoProbe.expectMsg(3.seconds, Subscribe(actor))
      accnRepoProbe.expectMsg(3.seconds, Subscribe(actor))
      accnRepoProbe.expectMsg(3.seconds, accountRepo.list.head.asPublicKey)
      accountRepo.list.map(_.asPublicKey).map(Account(_, 123L)).foreach(actor ! UpdateAccount(_))

      actor ! StreamInProgress(true)
      payments.foreach(actor ! _)
      actor ! StreamInProgress(false)

      payRepoProbe.expectMsgPF(3.seconds) {
        case UpdateStatus(List(_), Failed, Some("PaymentUnderfunded")) => ()
        case UpdateStatus(ps, Succeeded, None) if ps.size == 4 => ()
      }
      payRepoProbe.expectMsgPF(3.seconds) {
        case UpdateStatus(List(_), Failed, Some("PaymentUnderfunded")) => ()
        case UpdateStatus(ps, Succeeded, None) if ps.size == 4 => ()
      }
      eventually(timeout(3.seconds)) {
        val posted = config.network.asInstanceOf[StubNetwork].posted
        assert(posted.map(_.transaction.operations.size) == Seq(5, 4))
      }
    }
  }

  "handling transactions not being attempted" must {
    "remove the account from circulation if there is insufficient balance" in {
      val (payRepoProbe, accnRepoProbe, config, accnRepo) = setup(
        respondWith = Seq(
          TransactionRejected(1, "", "", Nil, "",
            ByteArrays.base64(TransactionNotAttempted(InsufficientBalance, NativeAmount(100)).encode)),
          TransactionApproved("", 1, "", "", "")
        ),
        numAccounts = 2
      )
      val payment = sampleOf(genScheduledPayment).copy(source = RawAccountId(accnRepo.list.head.accountId))

      val actor = system.actorOf(Props(new PaymentController(payRepoProbe.ref, accnRepoProbe.ref, config, accnRepo)))
      payRepoProbe.expectMsg(3.seconds, Subscribe(actor))
      accnRepoProbe.expectMsg(3.seconds, Subscribe(actor))
      accnRepoProbe.expectMsg(3.seconds, accnRepo.list.head.asPublicKey)
      accnRepo.list.map(_.asPublicKey).map(Account(_, 123L)).foreach(actor ! UpdateAccount(_))

      actor ! StreamInProgress(true)
      actor ! payment
      actor ! StreamInProgress(false)

      eventually(timeout(3.seconds)) {
        val posted = config.network.asInstanceOf[StubNetwork].posted
        assert(posted.map(_.transaction.operations.size) == Seq(1, 1))
        assert(posted.map(_.transaction.source).distinct.size == 2)
      }
    }

    "retry the payments and refresh the account if the sequence number is incorrect" in {
      val (payRepoProbe, accnRepoProbe, config, accnRepo) = setup(
        respondWith = Seq(
          TransactionRejected(1, "", "", Nil, "",
            ByteArrays.base64(TransactionNotAttempted(BadSequenceNumber, NativeAmount(100)).encode)),
          TransactionApproved("", 1, "", "", "")
        ),
        numAccounts = 2
      )
      val payment = sampleOf(genScheduledPayment).copy(source = RawAccountId(accnRepo.list.head.accountId))

      val actor = system.actorOf(Props(new PaymentController(payRepoProbe.ref, accnRepoProbe.ref, config, accnRepo)))
      payRepoProbe.expectMsg(3.seconds, Subscribe(actor))
      accnRepoProbe.expectMsg(3.seconds, Subscribe(actor))
      accnRepoProbe.expectMsg(3.seconds, accnRepo.list.head.asPublicKey)
      accnRepo.list.map(_.asPublicKey).map(Account(_, 123L)).foreach(actor ! UpdateAccount(_))

      actor ! StreamInProgress(true)
      actor ! payment
      actor ! StreamInProgress(false)

      eventually(timeout(3.seconds)) {
        val posted = config.network.asInstanceOf[StubNetwork].posted
        assert(posted.map(_.transaction.operations.size) == Seq(1, 1))
      }
      accnRepoProbe.expectMsg(3.seconds, accnRepo.list.last.asPublicKey)
    }

    "fail the payments and refresh the account for any other failure reason" in {
      val (payRepoProbe, accnRepoProbe, config, accnRepo) = setup(
        respondWith = Seq(
          TransactionRejected(1, "", "", Nil, "",
            ByteArrays.base64(TransactionNotAttempted(UnusedSignatures, NativeAmount(100)).encode))
        )
      )
      val payment = sampleOf(genScheduledPayment).copy(source = RawAccountId(accnRepo.list.head.accountId))

      val actor = system.actorOf(Props(new PaymentController(payRepoProbe.ref, accnRepoProbe.ref, config, accnRepo)))
      payRepoProbe.expectMsg(3.seconds, Subscribe(actor))
      accnRepoProbe.expectMsg(3.seconds, Subscribe(actor))
      accnRepoProbe.expectMsg(3.seconds, accnRepo.list.head.asPublicKey)
      accnRepo.list.map(_.asPublicKey).map(Account(_, 123L)).foreach(actor ! UpdateAccount(_))

      actor ! StreamInProgress(true)
      actor ! payment
      actor ! StreamInProgress(false)

      eventually(timeout(3.seconds)) {
        val posted = config.network.asInstanceOf[StubNetwork].posted
        assert(posted.map(_.transaction.operations.size) == Seq(1))
      }
      accnRepoProbe.expectMsg(3.seconds, accnRepo.list.last.asPublicKey)
      payRepoProbe.expectMsg(3.seconds, UpdateStatus(Seq(payment.copy(
        sourceResolved = Some(KeyPair.fromAccountId(payment.source.account)),
        destinationResolved = Some(KeyPair.fromAccountId(payment.destination.account))
      )), Failed, Some("UnusedSignatures")))
    }

    "refresh the account if there is a failure" in {
      val (payRepoProbe, accnRepoProbe, config, accnRepo) = setupForFailure
      val payment = sampleOf(genScheduledPayment).copy(source = RawAccountId(accnRepo.list.head.accountId))

      val actor = system.actorOf(Props(new PaymentController(payRepoProbe.ref, accnRepoProbe.ref, config, accnRepo)))
      payRepoProbe.expectMsg(3.seconds, Subscribe(actor))
      accnRepoProbe.expectMsg(3.seconds, Subscribe(actor))
      accnRepoProbe.expectMsg(3.seconds, accnRepo.list.head.asPublicKey)
      accnRepo.list.map(_.asPublicKey).map(Account(_, 123L)).foreach(actor ! UpdateAccount(_))

      actor ! StreamInProgress(true)
      actor ! payment
      actor ! StreamInProgress(false)

      accnRepoProbe.expectMsg(3.seconds, accnRepo.list.last.asPublicKey)
    }
  }

  "payment controller state" must {
    "add a pending payment, whilst decrementing the validations-in-flight counter" in {
      val s = PaymentController.State(
        accounts = Map(sampleAccount.publicKey.accountId -> sampleAccount),
        validationsInFlight = 2
      )
      val payment = sampleOf(genScheduledPayment)
      val (s_, batches) = s.addPending(payment)
      assert(s_.validationsInFlight == 1)
      assert(s_.valid == Seq(payment))
      assert(batches.isEmpty)
    }

    "flush a batch when adding the 100th pending payment" in {
      val payments = sampleOf(Gen.listOfN(100, genScheduledPayment))
      val s = PaymentController.State(
        valid = payments.tail,
        accounts = Map(sampleAccount.publicKey.accountId -> sampleAccount),
        validationsInFlight = 2
      )
      val (s_, batches) = s.addPending(payments.head)
      assert(s_.validationsInFlight == 1)
      assert(s_.valid.isEmpty)
      assert(s_.accounts.isEmpty)
      assert(batches == Seq(PaymentBatch(payments.reverse, sampleAccount)))
    }

    "not flush a batch when adding the 100th pending payment, but there are no free accounts" in {
      val payments = sampleOf(Gen.listOfN(100, genScheduledPayment))
      val s = PaymentController.State(
        valid = payments.tail,
        accounts = Map.empty,
        validationsInFlight = 2
      )
      val (s_, batches) = s.addPending(payments.head)
      assert(s_.validationsInFlight == 1)
      assert(s_.valid.size == 100)
      assert(s_.accounts.isEmpty)
      assert(batches.isEmpty)
    }

    "flush partial batches when requested" in {
      val s = PaymentController.State(
        valid = sampleOf(Gen.listOfN(20, genScheduledPayment)),
        accounts = Map(sampleAccount.publicKey.accountId -> sampleAccount)
      )
      val (s_, batches) = s.flush
      assert(s_.valid.isEmpty)
      assert(batches.size == 1)
      assert(batches.head.ps.size == 20)
    }
  }

  private def setupForFailure: (TestProbe, TestProbe, AppConfig, AccountDao) = {
    val accountRepo = mock[AccountDao]
    when(accountRepo.list).thenReturn(Seq(KeyPair.random))
    (TestProbe(), TestProbe(), new AppConfig {
      override val network: Network = StubNetwork(fail = true)
    }, accountRepo)
  }

  private def setup(numAccounts: Int = 1, respondWith: Seq[TransactionPostResponse] = Seq(TransactionApproved("", 1, "", "", "")))
  : (TestProbe, TestProbe, AppConfig, AccountDao) = {
    val accountRepo = mock[AccountDao]
    when(accountRepo.list).thenReturn((0 until numAccounts).map{_ => KeyPair.random})
    (TestProbe(), TestProbe(), new AppConfig {
      override val network: Network = StubNetwork(respondWith)
    }, accountRepo)
  }

}
