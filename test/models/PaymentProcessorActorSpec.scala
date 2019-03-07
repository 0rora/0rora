package models

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import models.PaymentProcessor.UpdateAccount
import models.repo.PaymentRepo
import org.mockito.Mockito.verify
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import org.scalatest.time.SpanSugar
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import play.api.Configuration
import stellar.sdk.KeyPair
import stellar.sdk.model.Account

class PaymentProcessorActorSpec extends TestKit(ActorSystem("payment-processor-spec")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with MockitoSugar with Eventually with SpanSugar {

  override def afterAll: Unit = TestKit.shutdownActorSystem(system)

  private val conf = new AppConfig(Configuration("0rora.horizon" -> "test", "0rora.accounts" -> Seq.empty[String]))
  private val accountA = KeyPair.fromPassphrase("account a")
  private def repo = mock[PaymentRepo]

  "a payment processor" must {
    "update an account" in {
      val cache = mock[AccountCache]
      val account = Account(accountA, 100)
      val actor = system.actorOf(Props(new PaymentProcessorActor(repo, cache, conf)))

      actor ! UpdateAccount(account)

      eventually(timeout(5 seconds)) { verify(cache).returnAccount(account) }
    }
  }


}
