package models

import actors.PaymentRepository.Poll
import actors.{AccountRepository, PaymentController, PaymentRepository}
import akka.actor.{ActorSystem, Props}
import javax.inject.{Inject, Singleton}
import models.db.{AccountDao, PaymentDao}
import play.api.inject.{Binding, BindingKey, Module}
import play.api.{Configuration, Environment}
import scalikejdbc.{AutoSession, DBSession}

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

@Singleton
class PaymentProcessor @Inject()(repo: PaymentDao,
                                 accountQueries: AccountDao,
                                 config: AppConfig,
                                 system: ActorSystem) {

  private implicit val ec: ExecutionContextExecutor = system.dispatcher

  private val payRepo = system.actorOf(Props(classOf[PaymentRepository], repo))
  private val accountRepo = system.actorOf(Props(classOf[AccountRepository], config))
  private val controller = system.actorOf(Props(classOf[PaymentController], payRepo, accountRepo, config, accountQueries))

  system.scheduler.scheduleOnce(3.seconds)(checkForPayments())

  def checkForPayments(): Unit = payRepo ! Poll
}
