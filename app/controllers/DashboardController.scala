package controllers

import akka.actor.ActorSystem
import controllers.actions.AuthenticatedUserAction
import javax.inject._
import models.Global
import models.Global.SESSION_USERNAME_KEY
import models.repo.UserRepo
import play.api.libs.concurrent.CustomExecutionContext
import play.api.mvc._

@Singleton
class DashboardController @Inject()(
                                     cc: MessagesControllerComponents,
                                     authenticatedUserAction: AuthenticatedUserAction
                                   ) extends MessagesAbstractController(cc) {
  def dashboard() = authenticatedUserAction { implicit req =>
    req.session.get(SESSION_USERNAME_KEY) match {
      case Some(username) => Ok(views.html.dashboard(username, routes.LoggedInController.logout()))
      case None => Ok("Huh?")
    }
  }
}
