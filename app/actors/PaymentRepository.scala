package actors

import java.time.temporal.ChronoUnit
import java.time.{LocalDateTime, Period, ZonedDateTime}

import actors.PaymentController.{FlushBatch, Invalid, Subscribe}
import actors.PaymentRepository._
import akka.actor.{Actor, ActorRef}
import models.Payment
import models.Payment.Status
import models.repo.PaymentRepo
import play.api.Logger

import scala.concurrent.duration._

class PaymentRepository(repo: PaymentRepo) extends Actor {

  import context.dispatcher

  override def receive: Receive = newState()

  private def newState(state: State = State()): Receive =
    subscribe(state) orElse poll(state) orElse schedulePoll orElse invalid orElse submitted orElse updateStatus orElse {
      case x => logger.warn(s"Unrecognised: $x")
    }

  def subscribe(state: State): PartialFunction[Any, Unit] = {
    case Subscribe(sub) =>
      logger.debug(s"Subscribing for payment updates: $sub")
      context.become(newState(state.addSubscriber(sub)))
  }

  def poll(state: State): PartialFunction[Any, Unit] = {
    case Poll =>
      logger.debug("Polling")
      // todo - convert to "update returning" statement - https://stackoverflow.com/questions/55213167/update-returning-queries-in-scalikejdbc
      repo.due().foreach(p => state.subs.foreach(_ ! p))
//        .grouped(100).foreach(ps => repo.submit(ps.flatMap(_.id), ZonedDateTime.now))
  }

  val schedulePoll: PartialFunction[Any, Unit] = {
    case SchedulePoll =>
      repo.earliestTimeDue match {
        case Some(zdt) =>
          val delay = Duration(math.max(0, ChronoUnit.SECONDS.between(ZonedDateTime.now, zdt)), SECONDS)
          context.system.scheduler.scheduleOnce(delay, self, Poll)
          logger.debug(s"Scheduling the next DB poll at $zdt ($delay)")
        case None =>
          logger.debug("Scheduling the next DB poll, but there are no future payments pending")
      }
  }

  val invalid: PartialFunction[Any, Unit] = {
    case Invalid(payment) =>
      val now = ZonedDateTime.now
      payment.id.foreach { id =>
        repo.invalidate(Seq(id), now) // todo - update single
      }
  }

  val submitted: PartialFunction[Any, Unit] = {
    case Submitted(ps) =>
      val now = ZonedDateTime.now
      logger.debug(s"Updating ${ps.size} payments as Submitted at $now: ${ps.flatMap(_.id).mkString("[",",","]")}")
      repo.submit(ps.flatMap(_.id), now)
  }

  val updateStatus: PartialFunction[Any, Unit] = {
    case UpdateStatus(ps, status, cause) =>
      logger.debug(s"Updating ${ps.size} payments as $status${cause.map(c => s"($c)").getOrElse("")}")
      repo.updateStatus(ps.flatMap(_.id), status) // todo - update with cause
  }
}

object PaymentRepository {

  val logger: Logger = Logger("0rora.payment_repository")

  case object Poll

  case object SchedulePoll

  case class UpdateStatus(ps: Seq[Payment], status: Status, cause: Option[String] = None)

  case class Submitted(ps: Seq[Payment])

  case class State(subs: Set[ActorRef] = Set.empty,
                   maxReceivedDate: Option[ZonedDateTime] = None) {

    def addSubscriber(sub: ActorRef): State = copy(subs + sub)

//    def notifySubscribers(p: Payment): State = subs.foreach(_ ! p)
//      maxReceivedDate match {
//        case Some(d) if d.isAfter(p.received) => this
//        case _ => copy(maxReceivedDate = Some(p.received))
//      }
//    }
  }

}