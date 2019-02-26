package controllers

import controllers.actions.AuthenticatedUserAction
import javax.inject._
import play.api.mvc._

@Singleton
class LoggedInController @Inject()(cc: ControllerComponents,
                                   authenticatedUserAction: AuthenticatedUserAction) extends AbstractController(cc) {

  def logout = authenticatedUserAction { implicit request: Request[AnyContent] =>
    Redirect(routes.HomeController.login())
      .flashing("info" -> "You are logged out.")
      .withNewSession
  }
}
