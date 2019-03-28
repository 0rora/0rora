package actors

import actors.AccountRepository.{State, logger}
import actors.PaymentController.{Subscribe, UpdateAccount}
import akka.actor.{Actor, ActorRef}
import models.AppConfig
import play.api.Logger
import stellar.sdk.{PublicKey, PublicKeyOps}

import scala.util.{Failure, Success}

class AccountRepository(config: AppConfig) extends Actor {

  import context.dispatcher

  override def receive: Receive = newState(State())

  private def newState(state: State): Receive = subscribe(state) orElse refreshAccount(state)

  def subscribe(state: State): PartialFunction[Any, Unit] = {
    case Subscribe(sub) =>
      logger.debug(s"Subscribing for account updates: $sub")
      context.become(newState(state.addSubscriber(sub)))
  }

  def refreshAccount(s: State): PartialFunction[Any, Unit] = {
    case pk: PublicKey =>
      logger.debug(s"[account ${pk.accountId}] Refreshing from network")
      config.network.account(pk).onComplete {
        case Success(resp) =>
          val msg = UpdateAccount(resp.toAccount)
          s.subs.foreach(_ ! msg)

        case Failure(t) =>
          logger.warn(s"[account ${pk.accountId}] Unable to fetch details", t)
      }
  }

}

object AccountRepository {

  val logger: Logger = Logger("0rora.account_repository")

  case class State(subs: Set[ActorRef] = Set.empty) {
    def addSubscriber(sub: ActorRef): State = copy(subs = subs + sub)
  }

}