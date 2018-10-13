package models

import akka.actor.Actor
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Source}
import controllers.flows.PaymentFlow
import models.repo.{Payment, PaymentRepo}
import play.api.Configuration

import scala.concurrent.ExecutionContextExecutor

class PaymentProcessor(repo: PaymentRepo, config: Configuration) extends Actor {

  implicit private val ec: ExecutionContextExecutor = context.dispatcher
  implicit private val materializer: ActorMaterializer = ActorMaterializer()

  private val paymentSink = Flow[Payment]
    .map(_.asOperation)
    .to(PaymentFlow(config).sink)

  override def receive: Receive = {

    case CheckForPayments =>
      val payments = repo.due
      if (payments.nonEmpty) {
        repo.submit(payments.flatMap(_.id))
        self ! Pay(payments)
      }
      repo.durationUntilNextDue.foreach(context.system.scheduler.scheduleOnce(_, self, CheckForPayments))

    case Pay(payments) =>
      println(s"Paying $payments")
      Source.fromIterator(() => payments.iterator).to(paymentSink).run()

  }
}

object CheckForPayments
case class Pay(payments: Seq[Payment])