package controllers

import controllers.actions.AuthenticatedUserAction
import javax.inject.Inject
import models.Global.SessionUsernameKey
import play.api.mvc.{Action, AnyContent, MessagesAbstractController, MessagesControllerComponents}

class DashboardController @Inject()(cc: MessagesControllerComponents,
                                    authenticatedUserAction: AuthenticatedUserAction) extends MessagesAbstractController(cc) {

  def dashboard(): Action[AnyContent] = authenticatedUserAction { implicit req =>
    val username = req.session(SessionUsernameKey)
    Ok(views.html.main(username))
  }
}