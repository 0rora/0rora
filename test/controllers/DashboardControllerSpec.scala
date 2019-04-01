package controllers

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.pac4j.core.context.Pac4jConstants
import org.pac4j.core.profile.CommonProfile
import org.specs2.ScalaCheck
import org.specs2.concurrent.ExecutionEnv
import org.specs2.execute.Results
import org.specs2.mock.Mockito
import play.api.libs.typedmap.TypedKey
import play.api.test.CSRFTokenHelper._
import play.api.test.{FakeRequest, PlaySpecification}

class DashboardControllerSpec(implicit ec: ExecutionEnv) extends PlaySpecification with Results with Mockito with ScalaCheck {

  implicit val sys: ActorSystem = ActorSystem("DashboardControllerSpec")
  implicit val mat: ActorMaterializer = ActorMaterializer()

  "fetching the dashboard" should {
    "be successful" >> {
      val controller = new DashboardController(Stubs.stubSecurityComponents())
      val result = controller.dashboard().apply(FakeRequest()
        .addAttr(TypedKey[CommonProfile](Pac4jConstants.USER_PROFILES), new CommonProfile)
        .withCSRFToken
      )
      contentAsString(result) must contain("0rora")
      status(result) mustEqual 200
    }
  }

}
