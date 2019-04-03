package modules

import models.PaymentProcessor
import play.api.{Configuration, Environment}
import play.api.inject.{Binding, Module}
import scalikejdbc.{AutoSession, DBSession}

class ApplicationModule extends Module {
  def bindings(env: Environment, config: Configuration): Seq[Binding[_]] =
    Seq(
      bind[PaymentProcessor].toSelf.eagerly(),
      bind[DBSession].to(AutoSession)
    )
}
