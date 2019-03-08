package models

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import models.PaymentProcessor.{RegisterAccount, UpdateAccount}
import models.repo.PaymentRepo
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import org.scalatest.time.SpanSugar
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import stellar.sdk.model.response.AccountResponse
import stellar.sdk.model.{Account, Thresholds}
import stellar.sdk.{KeyPair, Network}

class PaymentProcessorActorSpec extends TestKit(ActorSystem("payment-processor-spec")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with MockitoSugar with Eventually with SpanSugar {

  override def afterAll: Unit = TestKit.shutdownActorSystem(system)

  private val accountA = KeyPair.fromPassphrase("account a").asPublicKey

  "a payment processor" must {
    "update an account" in {
      val (_, conf, repo, cache) = setup
      val account = Account(accountA, 100)
      val actor = system.actorOf(Props(new PaymentProcessorActor(repo, cache, conf)))

      actor ! UpdateAccount(account)

      eventually(timeout(5 seconds)) {
        assert(cache.borrowAccount.contains(account))
      }
    }

    "fetch an account from the configured network, updating it on success" in {
      val (network, conf, repo, cache) = setup
      val account = Account(accountA, 101)

      network.expectAccount(accountA,
        AccountResponse(accountA, 101, 0, Thresholds(1, 2, 3), authRequired = false, authRevocable = true, Nil, Nil)
      )

      val actor = system.actorOf(Props(new PaymentProcessorActor(repo, cache, conf)))

      actor ! RegisterAccount(accountA)

      eventually(timeout(5 seconds)) {
        assert(cache.borrowAccount.contains(account.withIncSeq))
      }
    }
  }

  private def setup: (StubNetwork, AppConfig, PaymentRepo, AccountCache) = {
    val n = StubNetwork()
    val conf = new AppConfig {
      val network: Network = n
      val accounts: Map[String, KeyPair] = Map.empty
    }
    val repo = mock[PaymentRepo]
    (n, conf, repo, new AccountCache)
  }

}
