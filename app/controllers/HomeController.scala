package controllers

import akka.actor.ActorSystem
import javax.inject._
import models.Global
import models.Global.SESSION_USERNAME_KEY
import models.repo.UserRepo
import play.api.libs.concurrent.CustomExecutionContext
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

//trait LuxEC extends ExecutionContext
//
//class LuxExecutionContext @Inject()(system: ActorSystem) extends CustomExecutionContext(system, "lux-ec") with LuxEC


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

