package controllers

import controllers.actions.AuthenticatedUserAction
import models.Global.SessionUsernameKey
import org.specs2.concurrent.ExecutionEnv
import play.api.mvc.Results.Redirect
import play.api.mvc.{Request, Result}
import play.api.test.{FakeRequest, Injecting, PlaySpecification, WithApplication}

import scala.concurrent.Future
import scala.concurrent.duration._

class AuthenticatedUserActionSpec(implicit ee: ExecutionEnv) extends PlaySpecification {

  "the auth user action" should {
    "redirect the request if there's no session name" in new WithApplication() with Injecting  {
      val action = inject[AuthenticatedUserAction]
      val request = FakeRequest(GET, "/")
      def block[A](r: Request[A]): Future[Result] = Future(play.api.mvc.Results.Ok("done"))

      action.invokeBlock(request, block) must beEqualTo(Redirect(routes.HomeController.login())
        .flashing("info" -> "Please log in.")
        .withNewSession
      ).awaitFor(30 seconds)
    }

    "forward the request if there's a session name" in new WithApplication() with Injecting  {
      val action = inject[AuthenticatedUserAction]
      val request = FakeRequest(GET, "/").withSession(SessionUsernameKey -> "Bernard")
      def block[A](r: Request[A]): Future[Result] = Future(play.api.mvc.Results.Ok("done"))

      action.invokeBlock(request, block) must beEqualTo(play.api.mvc.Results.Ok("done"))
        .awaitFor(30 seconds)
    }
  }

}
