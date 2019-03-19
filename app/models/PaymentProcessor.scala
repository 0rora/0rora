package models

import actors.PaymentRepository.Poll
import actors.{AccountRepository, PaymentController, PaymentRepository}
import akka.actor.{ActorSystem, Props}
import javax.inject.{Inject, Singleton}
import models.repo.PaymentRepo
import play.api.inject.{Binding, BindingKey, Module}
import play.api.{Configuration, Environment}
import scalikejdbc.{AutoSession, DBSession}

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

@Singleton
class PaymentProcessor @Inject()(repo: PaymentRepo,
                                 config: AppConfig,
                                 system: ActorSystem) {

  private implicit val ec: ExecutionContextExecutor = system.dispatcher

  private val payRepo = system.actorOf(Props(classOf[PaymentRepository], repo))
  private val accountRepo = system.actorOf(Props(classOf[AccountRepository], config))
  private val controller = system.actorOf(Props(classOf[PaymentController], payRepo, accountRepo, config))

  system.scheduler.scheduleOnce(3.seconds)(checkForPayments())

  def checkForPayments(): Unit = payRepo ! Poll
}

class PaymentProcessorModule extends Module {
  def bindings(env: Environment, config: Configuration): Seq[Binding[_]] =
    Seq(
      bind[PaymentProcessor].toSelf.eagerly(),
      bind[DBSession].to(AutoSession)
    )
}
