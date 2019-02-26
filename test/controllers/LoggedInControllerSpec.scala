package controllers

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import controllers.actions.AuthenticatedUserAction
import models.Global.SessionUsernameKey
import org.specs2.ScalaCheck
import org.specs2.concurrent.ExecutionEnv
import org.specs2.execute.Results
import org.specs2.mock.Mockito
import play.api.mvc.{BodyParsers, Flash, Session}
import play.api.test.{FakeRequest, PlaySpecification}

class LoggedInControllerSpec (implicit ec: ExecutionEnv) extends PlaySpecification with Results with Mockito with ScalaCheck {

  implicit val sys: ActorSystem = ActorSystem("UserControllerSpec")
  implicit val mat: ActorMaterializer = ActorMaterializer()
  private val authUserAction = new AuthenticatedUserAction(new BodyParsers.Default())

  "Logging out" should {
    "redirect to / and start a new session" >> {
      val controller = new LoggedInController(Stubs.stubMessagesControllerComponents(), authUserAction)
      val result = controller.logout.apply(FakeRequest().withSession(SessionUsernameKey -> "anyone"))
      session(result) mustEqual Session()
      redirectLocation(result) must beSome("/")
      flash(result) mustEqual Flash(Map("info" -> "You are logged out."))
    }
  }


}
