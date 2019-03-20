package actors

import actors.PaymentController.Subscribe
import actors.PaymentRepository.Poll
import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import models.Generators._
import models.repo.PaymentRepo
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalacheck.Gen
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import org.scalatest.time.SpanSugar

class PaymentRepositorySpec extends TestKit(ActorSystem("payment-repository-spec")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with MockitoSugar with Eventually with SpanSugar {

  override def afterAll: Unit = TestKit.shutdownActorSystem(system)

  "subscribing to the repo, and then polling" must {
    "ensure due payments are delivered to the subscriber" in {
      val repo = mock[PaymentRepo]
      val payments = sampleOf(Gen.listOfN(5, genScheduledPayment))
      when(repo.due).thenReturn(payments.iterator)

      val actor = system.actorOf(Props(new PaymentRepository(repo)))
      val probe = TestProbe()
      val sub = probe.testActor

      actor ! Subscribe(sub)
      actor ! Poll

      payments.foreach(probe.expectMsg(5.seconds, _))
    }
  }

}
