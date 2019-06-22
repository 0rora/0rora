package actors

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

import actors.PaymentController.{Invalid, StreamInProgress, Subscribe}
import actors.PaymentRepository._
import akka.actor.{Actor, ActorRef}
import models.Payment
import models.Payment.Status
import models.db.PaymentDao
import play.api.Logger

import scala.concurrent.duration._

class PaymentRepository(repo: PaymentDao) extends Actor {

  import context.dispatcher

  override def receive: Receive = newState()

  private def newState(state: State = State()): Receive =
    subscribe(state) orElse poll(state) orElse schedulePoll orElse invalid orElse updateStatus

  def subscribe(state: State): PartialFunction[Any, Unit] = {
    case Subscribe(sub) =>
      logger.debug(s"Subscribing for payment updates: $sub")
      context.become(newState(state.addSubscriber(sub)))
  }

  def poll(state: State): PartialFunction[Any, Unit] = {
    case Poll =>
      logger.debug("Polling")
      state.subs.foreach(_ ! StreamInProgress(true))
      repo.due.foreach(p => state.subs.foreach(_ ! p))
      state.subs.foreach(_ ! StreamInProgress(false))
      self ! SchedulePoll
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
      payment.id.foreach(repo.invalidate(_, now))
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

  case class State(subs: Set[ActorRef] = Set.empty,
                   maxReceivedDate: Option[ZonedDateTime] = None) {

    def addSubscriber(sub: ActorRef): State = copy(subs + sub)

  }

}