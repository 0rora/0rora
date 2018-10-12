package models

import akka.actor.Actor
import models.repo.PaymentRepo

import scala.concurrent.ExecutionContextExecutor

class PaymentProcessor(repo: PaymentRepo) extends Actor {

  implicit val ec: ExecutionContextExecutor = context.dispatcher

  override def receive: Receive = {

    case CheckForPayments =>
      println(CheckForPayments)
      val payments = repo.due
      if (payments.nonEmpty) repo.submit(payments.flatMap(_.id))
      payments.foreach(println)
      repo.durationUntilNextDue.foreach(context.system.scheduler.scheduleOnce(_, self, CheckForPayments))

  }
}

object CheckForPayments