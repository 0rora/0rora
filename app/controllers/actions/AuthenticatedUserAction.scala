package controllers.actions

import controllers.routes
import javax.inject.Inject
import models.Global.SessionUsernameKey
import play.api.Logger
import play.api.mvc.{ActionBuilderImpl, BodyParsers, Request, Result}
import play.api.mvc.Results.Redirect

import scala.concurrent.{ExecutionContext, Future}

class AuthenticatedUserAction @Inject() (parser: BodyParsers.Default)(implicit ec: ExecutionContext)
  extends ActionBuilderImpl(parser) {

  private val logger = Logger("0rora.authentication")

  override def invokeBlock[A](request: Request[A], block: Request[A] => Future[Result]): Future[Result] = {
    logger.trace("Entered AuthenticatedUserAction.invokeBlock ...")
    request.session.get(SessionUsernameKey).map(_ => block(request)).getOrElse(
      Future.successful(Redirect(routes.HomeController.login())
        .flashing("info" -> "Please log in.")
        .withNewSession
      )
    )
  }
}
