package modules

import com.google.inject.{AbstractModule, Provides}
import models.InvalidConfig
import org.pac4j.core.client.Clients
import org.pac4j.core.config.Config
import org.pac4j.core.profile.CommonProfile
import org.pac4j.http.client.direct.DirectFormClient
import org.pac4j.http.client.indirect.FormClient
import org.pac4j.http.credentials.authenticator.test.SimpleTestUsernamePasswordAuthenticator
import org.pac4j.play.http.PlayHttpActionAdapter
import org.pac4j.play.scala.{DefaultSecurityComponents, Pac4jScalaTemplateHelper, SecurityComponents}
import org.pac4j.play.store.{PlayCookieSessionStore, PlaySessionStore, ShiroAesDataEncrypter}
import org.pac4j.play.{CallbackController, LogoutController}
import play.api.{Configuration, Environment}

class SecurityModule(environment: Environment, configuration: Configuration) extends AbstractModule {

  override def configure(): Unit = {
    val secretKey = configuration.get[String]("play.http.secret.key")
    if (secretKey.length < 16) throw InvalidConfig("Value for play.http.secret.key is too short. Must be at least 16 characters.")
    val sKey = secretKey.take(16)
    val dataEncrypter = new ShiroAesDataEncrypter(sKey)
    val playSessionStore = new PlayCookieSessionStore(dataEncrypter)

    bind(classOf[PlaySessionStore]).toInstance(playSessionStore)
    bind(classOf[SecurityComponents]).to(classOf[DefaultSecurityComponents])
    bind(classOf[Pac4jScalaTemplateHelper[CommonProfile]])

    // logout
    val logoutController = new LogoutController()
    logoutController.setDefaultUrl("/")
    bind(classOf[LogoutController]).toInstance(logoutController)
  }

  @Provides
  def provideFormClient: FormClient = {
    val authenticator_REPLACE_ME = new SimpleTestUsernamePasswordAuthenticator()
    new FormClient("/login", authenticator_REPLACE_ME)
  }

  @Provides
  def provideConfig(formClient: FormClient): Config = {
    val clients = new Clients(s"/callback", formClient)
    val config = new Config(clients)
    config.setHttpActionAdapter(new PlayHttpActionAdapter)
    config
  }

}
