package controllers

import controllers.actions.AuthenticatedUserAction
import play.api.libs.streams.Accumulator
import play.api.mvc._
import play.api.test.Helpers

object Stubs {

  private val stub = Helpers.stubControllerComponents()

  def stubMessagesControllerComponents(): MessagesControllerComponents = {
    DefaultMessagesControllerComponents(
      new DefaultMessagesActionBuilderImpl(Helpers.stubBodyParser(AnyContentAsEmpty), stub.messagesApi)(stub.executionContext),
      DefaultActionBuilder(stub.actionBuilder.parser)(stub.executionContext), stub.parsers,
      stub.messagesApi, stub.langs, stub.fileMimeTypes, stub.executionContext
    )
  }
}
