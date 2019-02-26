package controllers

import javax.inject.Inject
import models.Global.SessionUsernameKey
import models.User
import models.repo.UserRepo
import play.api.data.Form
import play.api.data.Forms.{mapping, nonEmptyText}
import play.api.mvc._

class UserController @Inject()(cc: MessagesControllerComponents, userRepo: UserRepo) extends MessagesAbstractController(cc) {

  def processLoginAttempt: Action[AnyContent] = Action { implicit req: MessagesRequest[AnyContent] =>
    UserController.form.bindFromRequest.fold(
      hasErrors = { form: Form[User] =>
        BadRequest(views.html.login(form, routes.UserController.processLoginAttempt()))
      },
      success = { user: User =>
        if (userRepo.lookupUser(user)) {
          Redirect(routes.DashboardController.dashboard())
            .flashing("info" -> s"Welcome, ${user.username}.")
            .withSession(SessionUsernameKey -> user.username)
        } else {
          Redirect(routes.HomeController.login())
            .flashing("info" -> "Invalid username/password.")
        }
      }
    )
  }
}

object UserController {
  val form: Form[User] = Form (
    mapping(
      "username" -> nonEmptyText,
      "password" -> nonEmptyText
    )(User.apply)(User.unapply)
  )
}