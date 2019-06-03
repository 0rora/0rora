package models

import actors.AccountRepository
import akka.actor.ActorSystem
import models.repo.{AccountRepo, PaymentRepo}
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.MatchSuccess
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import stellar.sdk.model.{Account, Thresholds}
import stellar.sdk.model.response.AccountResponse
import stellar.sdk.{KeyPair, Network}

import scala.annotation.tailrec
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Success, Try}

class PaymentProcessorSpec(implicit ee: ExecutionEnv) extends Specification with Mockito {

  val system = ActorSystem("payment-processor-spec")

  "a payment processor" should {
    "start up after 3 seconds" >> {
      val source = KeyPair.random
      val payRepo = mock[PaymentRepo]
      payRepo.due returns Iterator.empty
      val accountQueries = mock[AccountRepo]

      val nw = StubNetwork()
      nw.expectAccount(source.asPublicKey,
        AccountResponse(source.asPublicKey, 1L, 0, Thresholds(1, 2, 3), authRequired = false, authRevocable = false, Nil, Nil))

      val config = new AppConfig {
        override val network: Network = nw
      }

      new PaymentProcessor(payRepo, accountQueries, config, system)

      Future {
        @tailrec
        def wait: Boolean = {
          Try(there was one(payRepo).due) match {
            case Success(MatchSuccess(_, _, _)) => true
            case _ =>
              Thread.sleep(250L)
              wait
          }
        }
        wait
      } must beTrue.awaitFor(timeout = 10.seconds)

    }
  }

}
