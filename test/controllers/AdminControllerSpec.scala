package controllers

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import models.FakeAppConfig
import models.Generators.genKeyPair
import models.db.AccountDao
import org.scalacheck.Arbitrary
import org.specs2.ScalaCheck
import org.specs2.concurrent.ExecutionEnv
import org.specs2.execute.Results
import org.specs2.mock.Mockito
import play.api.libs.json.Reads._
import play.api.libs.json.{Json, _}
import play.api.test.{FakeRequest, PlaySpecification}
import stellar.sdk.KeyPair

class AdminControllerSpec(implicit ec: ExecutionEnv) extends PlaySpecification with Results with Mockito with ScalaCheck {
  implicit val sys: ActorSystem = ActorSystem(getClass.getSimpleName)
  implicit val mat: ActorMaterializer = ActorMaterializer()

  implicit val arbKeyPair: Arbitrary[KeyPair] = Arbitrary(genKeyPair)

  "GET listAccounts" should {
    "return all the accounts" >> prop { kps: Seq[KeyPair] =>
      val accountDao = mock[AccountDao]
      accountDao.list returns kps
      val controller = new AdminController(Stubs.stubSecurityComponents(), accountDao, new FakeAppConfig())
      val result = controller.listAccounts().apply(FakeRequest())
      val bodyText: String = contentAsString(result)
      val list = Json.parse(bodyText).as[List[Map[String, JsValue]]]
        .map(_.apply("id")).map {
        case JsString(s) => s
        case x => x.toString
      }
      list must containTheSameElementsAs(kps.map(_.accountId))
    }
  }

}
