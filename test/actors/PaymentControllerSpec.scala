package actors

import actors.PaymentController.{Subscribe, UpdateAccount}
import actors.PaymentRepository.SchedulePoll
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import models.Generators.{genScheduledPayment, sampleOf}
import models.{AppConfig, Generators, RawAccountId, StubNetwork}
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import org.scalatest.time.SpanSugar
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import stellar.sdk.model.{Account, NativeAmount}
import stellar.sdk.model.op.PaymentOperation
import stellar.sdk.{KeyPair, Network}

class PaymentControllerSpec extends TestKit(ActorSystem("payment-controller-spec")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with MockitoSugar with Eventually with SpanSugar {

  override def afterAll: Unit = TestKit.shutdownActorSystem(system)

  "the payment controller" must {
    "subscribe to helpers and register configured accounts on startup" in {
      val (payRepoProbe, accnRepoProbe, config) = setup

      val actor = system.actorOf(Props(new PaymentController(payRepoProbe.ref, accnRepoProbe.ref, config)))

      payRepoProbe.expectMsg(3.seconds, Subscribe(actor))
      accnRepoProbe.expectMsg(3.seconds, Subscribe(actor))
      accnRepoProbe.expectMsg(3.seconds, config.accounts.head._2.asPublicKey)
    }
  }

  "a pending payment" must {
    "be processed in a batch when valid" in {
      val (payRepoProbe, accnRepoProbe, config) = setup
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


  private def setup: (TestProbe, TestProbe, AppConfig) = {
    (TestProbe(), TestProbe(), new AppConfig {
      override val network: Network = StubNetwork()
      override val accounts: Map[String, KeyPair] = {
        val kp = KeyPair.random
        Map(kp.accountId -> kp)
      }
    })
  }

}
