package actors

import actors.PaymentController.{Subscribe, UpdateAccount}
import actors.PaymentRepository.{SchedulePoll, UpdateStatus}
import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import models.Generators.{genScheduledPayment, sampleOf}
import models.{AppConfig, RawAccountId, StubNetwork}
import org.scalacheck.Gen
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import org.scalatest.time.SpanSugar
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import stellar.sdk.model.op.PaymentOperation
import stellar.sdk.model.{Account, NativeAmount}
import stellar.sdk.{KeyPair, Network}

class PaymentControllerSpec extends TestKit(ActorSystem("payment-controller-spec")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with MockitoSugar with Eventually with SpanSugar {

  override def afterAll: Unit = TestKit.shutdownActorSystem(system)

  private val sampleAccount = Account(KeyPair.random, 1)

  "the payment controller" must {
    "subscribe to helpers and register configured accounts on startup" in {
      val (payRepoProbe, accnRepoProbe, config) = setup()

      val actor = system.actorOf(Props(new PaymentController(payRepoProbe.ref, accnRepoProbe.ref, config)))

      payRepoProbe.expectMsg(3.seconds, Subscribe(actor))
      accnRepoProbe.expectMsg(3.seconds, Subscribe(actor))
      accnRepoProbe.expectMsg(3.seconds, config.accounts.head._2.asPublicKey)
    }
  }

  "a pending payment" must {
    "be processed in a batch when valid" in {
      val (payRepoProbe, accnRepoProbe, config) = setup()
      val payment = sampleOf(genScheduledPayment).copy(source = RawAccountId(config.accounts.head._1))
      val accn = Account(config.accounts.head._2.asPublicKey, 123L)

      val actor = system.actorOf(Props(new PaymentController(payRepoProbe.ref, accnRepoProbe.ref, config)))
      payRepoProbe.expectMsg(3.seconds, Subscribe(actor))
      accnRepoProbe.expectMsg(3.seconds, Subscribe(actor))
      accnRepoProbe.expectMsg(3.seconds, config.accounts.head._2.asPublicKey)
      actor ! UpdateAccount(accn)

      actor ! payment

      payRepoProbe.expectMsg(3.seconds, SchedulePoll)
      eventually(timeout(3.seconds)) {
        val posted = config.network.asInstanceOf[StubNetwork].posted
        assert(posted.size == 1)
        assert(posted.head.transaction.operations == Seq(PaymentOperation(
          destinationAccount = KeyPair.fromAccountId(payment.destination.account),
          amount = NativeAmount(payment.units),
          sourceAccount = Some(config.accounts.head._2.asPublicKey)
        )))
      }
    }
  }

  "many pending payments" must {
    "be processed in several batches when valid" in {
      val (payRepoProbe, accnRepoProbe, config) = setup(numAccounts = 2)
      val payments = sampleOf(Gen.listOfN(120, genScheduledPayment)).map(_.copy(source = RawAccountId(config.accounts.head._1)))
      val actor = system.actorOf(Props(new PaymentController(payRepoProbe.ref, accnRepoProbe.ref, config)))
      payRepoProbe.expectMsg(3.seconds, Subscribe(actor))
      accnRepoProbe.expectMsg(3.seconds, Subscribe(actor))
      accnRepoProbe.expectMsg(3.seconds, config.accounts.head._2.asPublicKey)
      actor ! UpdateAccount(Account(config.accounts.head._2.asPublicKey, 123L))
      actor ! UpdateAccount(Account(config.accounts.last._2.asPublicKey, 123L))

      payments.foreach(actor ! _)

      eventually(timeout(3.seconds)) {
        val posted = config.network.asInstanceOf[StubNetwork].posted
        assert(posted.size == 2)
        assert(posted.head.transaction.operations.size == 100)
        assert(posted.last.transaction.operations.size == 20)
      }
    }
  }

  "payment controller state" must {
    "flush partial batches when requested" in {
      val s = PaymentController.State(
        valid = sampleOf(Gen.listOfN(20, genScheduledPayment)),
        accounts = Map(sampleAccount.publicKey.accountId -> sampleAccount)
      )
      val (s_, batch) = s.flush
      assert(s_.valid.isEmpty)
      assert(batch.size == 1)
      assert(batch.head.ps.size == 20)
    }
  }


  private def setup(numAccounts: Int = 1): (TestProbe, TestProbe, AppConfig) = {
    val accns = (0 until numAccounts).map{_ =>
      val kp = KeyPair.random
      kp.accountId -> kp
    }.toMap
    (TestProbe(), TestProbe(), new AppConfig {
      override val network: Network = StubNetwork()
      override val accounts: Map[String, KeyPair] = accns
    })
  }

}
