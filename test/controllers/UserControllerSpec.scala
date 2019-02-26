package controllers

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import models.repo.UserRepo
import org.specs2.ScalaCheck
import org.specs2.concurrent.ExecutionEnv
import org.specs2.execute.Results
import org.specs2.mock.Mockito
import play.api.mvc.{Flash, Result}
import play.api.test.{FakeRequest, PlaySpecification}
import play.api.test.CSRFTokenHelper._

import scala.concurrent.Future

class UserControllerSpec(implicit ec: ExecutionEnv) extends PlaySpecification with Results {

  implicit val sys: ActorSystem = ActorSystem("UserControllerSpec")
  implicit val mat: ActorMaterializer = ActorMaterializer()

  "Processing a login attempt" should {
    "fail the attempt when the form values are not present" >> {
      status(result(Map.empty)) mustEqual 400
    }

    "fail the attempt when the username is not present" >> {
      status(result(Map("password" -> "moffens"))) mustEqual 400
    }

    "fail the attempt when the password is not present" >> {
      status(result(Map("username" -> "delishus"))) mustEqual 400
    }

    "reload the login page when the credentials are invalid" >> {
      val eventualResult = result(Map("username" -> "demo", "password" -> "bagels"))
      status(eventualResult) mustEqual 303
      redirectLocation(eventualResult) must beSome("/")
      flash(eventualResult) mustEqual Flash(Map("info" -> "Invalid username/password."))
    }

    "succeed when the credentials are valid" >> {
      val eventualResult = result(Map("username" -> "demo", "password" -> "demo"))
      status(eventualResult) mustEqual 303
      redirectLocation(eventualResult) must beSome("/dashboard")
      flash(eventualResult) mustEqual Flash(Map("info" -> "Welcome, demo."))
    }
  }

  private def result(form: Map[String, String]): Future[Result] = {
    val controller = new UserController(Stubs.stubMessagesControllerComponents(), new UserRepo)
    val fullForm = ("id" -> "login-form") +: form.toSeq
    val request = FakeRequest().withFormUrlEncodedBody(fullForm: _*).withCSRFToken
    controller.processLoginAttempt().apply(request)
  }

}
