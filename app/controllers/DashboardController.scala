package controllers

import controllers.actions.AuthenticatedUserAction
import javax.inject.Inject
import models.Global.SESSION_USERNAME_KEY
import play.api.mvc.{MessagesAbstractController, MessagesControllerComponents}

class DashboardController @Inject()(cc: MessagesControllerComponents,
                                    authenticatedUserAction: AuthenticatedUserAction
                                      ) extends MessagesAbstractController(cc) {

  def dashboard() = authenticatedUserAction { implicit req =>
    val username = req.session(SESSION_USERNAME_KEY)
    Ok(views.html.main(username))
  }
}