package controllers

import javax.inject._
import models.repo.UserRepo
import play.api.Configuration
import play.api.mvc._

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(cc: MessagesControllerComponents, userRepo: UserRepo) extends MessagesAbstractController(cc) {

  /**
   * Create an Action to render an HTML page.
   *
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */
  def login() = Action { implicit req =>
    Ok(views.html.login(UserController.form, routes.UserController.processLoginAttempt()))
  }
}

