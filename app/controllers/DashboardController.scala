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
    val username = req.session(SESSION_USERNAME_KEY)
    Ok(views.html.dashboard(Seq("Sources", "CSV"), username, routes.LoggedInController.logout()))
  }
}
