package actors

import java.time.ZonedDateTime

import actors.PaymentController.{Invalid, StreamInProgress, Subscribe}
import actors.PaymentRepository.{Poll, SchedulePoll, UpdateStatus}
import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import models.Generators._
import models.Payment.Failed
import models.repo.PaymentRepo
import org.mockito.ArgumentMatchers.any
import org.mockito.{ArgumentMatchers, Mockito}
import org.mockito.Mockito.{verify, when}
import org.scalacheck.Gen
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import org.scalatest.time.SpanSugar
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class PaymentRepositorySpec extends TestKit(ActorSystem("payment-repository-spec")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with MockitoSugar with Eventually with SpanSugar {

  override def afterAll: Unit = TestKit.shutdownActorSystem(system)

  "subscribing to the repo, and then polling" must {
    "ensure due payments are delivered to the subscriber" in {
      val repo = mock[PaymentRepo]
      val payments = sampleOf(Gen.listOfN(5, genScheduledPayment))
      when(repo.due).thenReturn(payments.iterator)
      when(repo.earliestTimeDue).thenReturn(None)

      val actor = system.actorOf(Props(new PaymentRepository(repo)))
      val probe = TestProbe()
      val sub = probe.testActor

      actor ! Subscribe(sub)
      actor ! Poll

      probe.expectMsg(5.seconds, StreamInProgress(true))
      payments.foreach(probe.expectMsg(5.seconds, _))
      probe.expectMsg(5.seconds, StreamInProgress(false))
    }
  }

  "scheduling a poll" must {
    "poll immediately when there are payments due" in {
      val repo = mock[PaymentRepo]
      when(repo.earliestTimeDue).thenReturn(Some(ZonedDateTime.now().minusMinutes(1)), None)
      when(repo.due).thenReturn(Iterator.empty)
      val actor = system.actorOf(Props(new PaymentRepository(repo)))

      actor ! SchedulePoll

      eventually(timeout(5 seconds)) {
        verify(repo).due // a side-effect of polling
      }
    }

    "not poll when there are no scheduled payments" in {
      val repo = mock[PaymentRepo]
      when(repo.earliestTimeDue).thenReturn(None)

      val actor = system.actorOf(Props(new PaymentRepository(repo)))

      actor ! SchedulePoll

      Thread.sleep(1000L)
      verify(repo, Mockito.never()).due
    }

    "poll after delay when the next scheduled payment is in the future" in {
      val repo = mock[PaymentRepo]
      val actor = system.actorOf(Props(new PaymentRepository(repo)))

      when(repo.due).thenReturn(Iterator.empty)
      when(repo.earliestTimeDue).thenReturn(Some(ZonedDateTime.now().plusSeconds(2)), None)
      actor ! SchedulePoll

      Thread.sleep(500L)
      verify(repo, Mockito.never()).due

      eventually(timeout(5 seconds)) {
        verify(repo).due // a side-effect of polling
      }
    }
  }

  "marking a payment invalid" must {
    "delegate to the db class" in {
      val repo = mock[PaymentRepo]
      val actor = system.actorOf(Props(new PaymentRepository(repo)))
      val payment = sampleOf(genScheduledPayment)

      actor ! Invalid(payment)

      eventually(timeout(5 seconds)) {
        verify(repo).invalidate(ArgumentMatchers.eq(payment.id.get), any[ZonedDateTime])
      }
    }
  }

  "marking a payment invalid" must {
    "delegate to invalidate method in the db class" in {
      val repo = mock[PaymentRepo]
      val actor = system.actorOf(Props(new PaymentRepository(repo)))
      val payment = sampleOf(genScheduledPayment)

      actor ! Invalid(payment)

      eventually(timeout(5 seconds)) {
        verify(repo).invalidate(ArgumentMatchers.eq(payment.id.get), any[ZonedDateTime])
      }
    }
  }

  "updating the status of payments" must {
    "delegate to updateStatus method on the db class" in {
      val repo = mock[PaymentRepo]
      val payments = sampleOf(Gen.listOfN(5, genScheduledPayment))
      val actor = system.actorOf(Props(new PaymentRepository(repo)))

      actor ! UpdateStatus(payments, Failed, Some("reason"))

      eventually(timeout(5 seconds)) {
        verify(repo).updateStatus(payments.flatMap(_.id), Failed)
      }
    }
  }

}
