package modules

import com.google.inject.Provides
import com.zaxxer.hikari.HikariDataSource
import models.InvalidConfig
import org.apache.shiro.authc.credential.DefaultPasswordService
import org.pac4j.core.client.Clients
import org.pac4j.core.config.Config
import org.pac4j.core.credentials.password.ShiroPasswordEncoder
import org.pac4j.http.client.indirect.FormClient
import org.pac4j.play.LogoutController
import org.pac4j.play.http.PlayHttpActionAdapter
import org.pac4j.play.scala.{DefaultSecurityComponents, SecurityComponents}
import org.pac4j.play.store.{DataEncrypter, PlayCookieSessionStore, PlaySessionStore, ShiroAesDataEncrypter}
import org.pac4j.sql.profile.service.DbProfileService
import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}

class SecurityModule extends Module {

  def bindings(env: Environment, config: Configuration): Seq[Binding[_]] = {
    val playSessionStore = new PlayCookieSessionStore(provideEncrypter(config))

    val logoutController = new LogoutController()
    logoutController.setDefaultUrl("/")

    Seq(
      bind[PlaySessionStore].toInstance(playSessionStore),
      bind[SecurityComponents].to(classOf[DefaultSecurityComponents]),
      bind[LogoutController].toInstance(logoutController)
    )
  }

  @Provides
  def provideEncrypter(configuration: Configuration): DataEncrypter = {
    val secretKey = configuration.get[String]("play.http.secret.key")
    if (secretKey.length < 16) throw InvalidConfig("Value for play.http.secret.key is too short. Must be at least 16 characters.")
    val sKey = secretKey.take(16)
    new ShiroAesDataEncrypter(sKey)
  }

  @Provides
  def provideAuthenticator(configuration: Configuration): DbProfileService = {
    val ds = new HikariDataSource()
    ds.setDriverClassName(configuration.get[String]("db.default.driver"))
    ds.setJdbcUrl(configuration.get[String]("db.default.url"))
    ds.setUsername(configuration.get[String]("db.default.username"))
    ds.setPassword(configuration.get[String]("db.default.password"))
    val passwordEncoder = new ShiroPasswordEncoder(new DefaultPasswordService)
    new DbProfileService(ds, passwordEncoder)
  }

  @Provides
  def provideFormClient(authenticator: DbProfileService): FormClient = {
    new FormClient("/login", authenticator)
  }

  @Provides
  def provideConfig(formClient: FormClient): Config = {
    val clients = new Clients(s"/callback", formClient)
    val config = new Config(clients)
    config.setHttpActionAdapter(new PlayHttpActionAdapter)
    config
  }

}
