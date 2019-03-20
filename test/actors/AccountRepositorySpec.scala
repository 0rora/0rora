package actors

import actors.PaymentController.{Subscribe, UpdateAccount}
import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import models.Generators.{genAccount, sampleOf}
import models.{AppConfig, Generators, StubNetwork}
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import org.scalatest.time.SpanSugar
import stellar.sdk.model.Thresholds
import stellar.sdk.model.response.AccountResponse
import stellar.sdk.{KeyPair, Network}

import scala.concurrent.Future

class AccountRepositorySpec extends TestKit(ActorSystem("account-repository-spec")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with MockitoSugar with Eventually with SpanSugar {

  import system.dispatcher

  override def afterAll: Unit = TestKit.shutdownActorSystem(system)

  "subscribing to the account updates" must {
    "result in the subscriber being notified on account update" in {
      val accn = sampleOf(genAccount)
      val response = AccountResponse(accn.publicKey.asPublicKey, 101, 0, Thresholds(1, 2, 3), authRequired = false, authRevocable = true, Nil, Nil)
      val n = StubNetwork()
      n.expectAccount(accn.publicKey, response)
      val conf = new AppConfig() {
        override val network: Network = n
        override val accounts: Map[String, KeyPair] = Map.empty
      }

      val actor = system.actorOf(Props(new AccountRepository(conf)))
      val probe = TestProbe()

      actor ! Subscribe(probe.ref)
      actor ! accn.publicKey.asPublicKey

      probe.expectMsg(3 seconds, UpdateAccount(response))
    }
  }

  "refresh account" must {
    "do nothing if the lookup fails" in {
      val n = mock[Network]
      val accn = sampleOf(genAccount)
      when(n.account(accn.publicKey.asPublicKey)).thenReturn(Future.failed(new RuntimeException("!")))
      val conf = new AppConfig() {
        override val network: Network = n
        override val accounts: Map[String, KeyPair] = Map.empty
      }

      val actor = system.actorOf(Props(new AccountRepository(conf)))
      val probe = TestProbe()

      actor ! Subscribe(probe.ref)
      actor ! accn.publicKey.asPublicKey

      probe.expectNoMessage(1 second)
    }
  }

}
