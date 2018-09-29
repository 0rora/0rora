package controllers

import javax.inject.Inject
import models.Global.SESSION_USERNAME_KEY
import models.User
import models.repo.UserRepo
import play.api.data.Form
import play.api.data.Forms.{mapping, nonEmptyText}
import play.api.mvc.{AnyContent, MessagesAbstractController, MessagesControllerComponents, MessagesRequest}

class UserController @Inject()(cc: MessagesControllerComponents, userRepo: UserRepo) extends MessagesAbstractController(cc) {

  def processLoginAttempt = Action { implicit req: MessagesRequest[AnyContent] =>
    UserController.form.bindFromRequest.fold(
      hasErrors = { form: Form[User] =>
        BadRequest(views.html.login(form, routes.UserController.processLoginAttempt()))
      },
      success = { user: User =>
        if (userRepo.lookupUser(user)) {
          Redirect(routes.PaymentsListController.dashboard())
            .flashing("info" -> s"Welcome, ${user.username}.")
            .withSession(SESSION_USERNAME_KEY -> user.username)
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