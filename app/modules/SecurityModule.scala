package modules

import com.google.inject.{AbstractModule, Provides}
import com.zaxxer.hikari.HikariDataSource
import models.InvalidConfig
import org.apache.shiro.authc.credential.DefaultPasswordService
import org.pac4j.core.client.Clients
import org.pac4j.core.config.Config
import org.pac4j.core.context.Pac4jConstants
import org.pac4j.core.credentials.password.ShiroPasswordEncoder
import org.pac4j.core.profile.CommonProfile
import org.pac4j.http.client.indirect.FormClient
import org.pac4j.play.LogoutController
import org.pac4j.play.http.PlayHttpActionAdapter
import org.pac4j.play.scala.{DefaultSecurityComponents, Pac4jScalaTemplateHelper, SecurityComponents}
import org.pac4j.play.store.{DataEncrypter, PlayCookieSessionStore, PlaySessionStore, ShiroAesDataEncrypter}
import org.pac4j.sql.profile.DbProfile
import org.pac4j.sql.profile.service.DbProfileService
import play.api.{Configuration, Environment}

class SecurityModule(environment: Environment, configuration: Configuration) extends AbstractModule {

  // $COVERAGE-OFF$
  override def configure(): Unit = {
    val playSessionStore = new PlayCookieSessionStore(provideEncrypter)

    bind(classOf[PlaySessionStore]).toInstance(playSessionStore)
    bind(classOf[SecurityComponents]).to(classOf[DefaultSecurityComponents])
    bind(classOf[Pac4jScalaTemplateHelper[CommonProfile]])

    // logout
    val logoutController = new LogoutController()
    logoutController.setDefaultUrl("/")
    bind(classOf[LogoutController]).toInstance(logoutController)

    // todo - Creation of first user is to be handled differently.
    val authenticator = provideAuthenticator
    if (Option(authenticator.findById("admin")).isEmpty) {
      val p = new DbProfile()
      p.setId("admin")
      p.addAttribute(Pac4jConstants.USERNAME, "admin")
      authenticator.create(p, "admin")
    }
  }
  // $COVERAGE-ON$

  @Provides
  def provideEncrypter: DataEncrypter = {
    val secretKey = configuration.get[String]("play.http.secret.key")
    if (secretKey.length < 16) throw InvalidConfig("Value for play.http.secret.key is too short. Must be at least 16 characters.")
    val sKey = secretKey.take(16)
    new ShiroAesDataEncrypter(sKey)
  }

  @Provides
  def provideAuthenticator: DbProfileService = {
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
