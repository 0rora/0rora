package controllers

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.specs2.ScalaCheck
import org.specs2.concurrent.ExecutionEnv
import org.specs2.execute.Results
import org.specs2.mock.Mockito
import play.api.test.CSRFTokenHelper._
import play.api.test.{FakeRequest, PlaySpecification}

class HomeControllerSpec(implicit ec: ExecutionEnv) extends PlaySpecification with Results with Mockito with ScalaCheck {

  implicit val sys: ActorSystem = ActorSystem("HomeControllerSpec")
  implicit val mat: ActorMaterializer = ActorMaterializer()

  "the home page" should {
    "display login form" >> {
/*
      val controller = new HomeController(Stubs.stubMessagesControllerComponents())
      val result = controller.login().apply(FakeRequest().withCSRFToken)
      contentAsString(result) must contain("login-form")
      status(result) mustEqual 200
*/
      pending
    }
  }

}
