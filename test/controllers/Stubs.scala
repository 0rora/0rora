package controllers

import org.pac4j.core.client.Clients
import org.pac4j.core.config.Config
import org.pac4j.core.context.Pac4jConstants
import org.pac4j.core.profile.{CommonProfile, ProfileManager}
import org.pac4j.http.client.indirect.FormClient
import org.pac4j.http.credentials.authenticator.test.SimpleTestUsernamePasswordAuthenticator
import org.pac4j.play.http.PlayHttpActionAdapter
import org.pac4j.play.scala.{DefaultSecurityComponents, SecurityComponents}
import org.pac4j.play.store.{PlayCacheSessionStore, PlayCookieSessionStore, PlaySessionStore}
import org.specs2.mock.Mockito
import play.api.mvc._
import play.api.test.Helpers

object Stubs extends Mockito {

  private val cc = Helpers.stubControllerComponents()

  def stubMessagesControllerComponents(): MessagesControllerComponents = {
    DefaultMessagesControllerComponents(
      new DefaultMessagesActionBuilderImpl(Helpers.stubBodyParser(AnyContentAsEmpty), cc.messagesApi)(cc.executionContext),
      DefaultActionBuilder(cc.actionBuilder.parser)(cc.executionContext), cc.parsers,
      cc.messagesApi, cc.langs, cc.fileMimeTypes, cc.executionContext
    )
  }

  def stubSecurityComponents(loggedIn: Boolean = true): SecurityComponents = {
    val client = new FormClient("/login", new SimpleTestUsernamePasswordAuthenticator)
    val config = new Config(new Clients("/callback", client))
    config.setProfileManagerFactory { ctx =>
      if (loggedIn) ctx.setRequestAttribute(Pac4jConstants.USER_PROFILES, new CommonProfile)
      new ProfileManager(ctx)
    }
    config.setHttpActionAdapter(new PlayHttpActionAdapter)
    DefaultSecurityComponents(
      mock[PlaySessionStore],
      config,
      new BodyParsers.Default(mock[DefaultPlayBodyParsers]),
      cc
    )
  }

}
