package controllers

import controllers.actions.AuthenticatedUserAction
import javax.inject._
import play.api.mvc._

@Singleton
class LoggedInController @Inject()(cc: ControllerComponents,
                                   authenticatedUserAction: AuthenticatedUserAction) extends AbstractController(cc) {

  def logout = authenticatedUserAction { implicit request: Request[AnyContent] =>
    // docs: “withNewSession ‘discards the whole (old) session’”
    Redirect(routes.HomeController.login())
      .flashing("info" -> "You are logged out.")
      .withNewSession
  }

}
