package controllers

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.specs2.ScalaCheck
import org.specs2.concurrent.ExecutionEnv
import org.specs2.execute.Results
import org.specs2.mock.Mockito
import play.api.mvc.{BodyParsers, Flash, Session}
import play.api.test.{FakeRequest, PlaySpecification}
import play.api.test.CSRFTokenHelper._

class DashboardControllerSpec(implicit ec: ExecutionEnv) extends PlaySpecification with Results with Mockito with ScalaCheck {

  implicit val sys: ActorSystem = ActorSystem("DashboardControllerSpec")
  implicit val mat: ActorMaterializer = ActorMaterializer()

  "fetching the dashboard" should {
    "be successful when logged in" >> {
/*
      val controller = new DashboardController(Stubs.stubMessagesControllerComponents())
      val result = controller.dashboard().apply(FakeRequest().withSession(SessionUsernameKey -> "anyone").withCSRFToken)
      contentAsString(result) must contain("0rora")
      status(result) mustEqual 200
*/
      pending
    }

    "redirect to login page when not logged in" >> {
/*
      val controller = new DashboardController(Stubs.stubMessagesControllerComponents())
      val result = controller.dashboard().apply(FakeRequest().withCSRFToken)
      session(result) mustEqual Session()
      redirectLocation(result) must beSome("/")
      status(result) mustEqual 303
*/
      pending
    }
  }

}
