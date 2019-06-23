package controllers

import java.sql.SQLException

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
import stellar.sdk.model.Thresholds
import stellar.sdk.model.response.AccountResponse

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

  "POST addAccount" should {
    "fail when the seed is not provided" >> {
      val controller = new AdminController(Stubs.stubSecurityComponents(), mock[AccountDao], new FakeAppConfig())
      val result = controller.addAccount().apply(FakeRequest())
      status(result) mustEqual 400
      val bodyText: String = contentAsString(result)
      val error = Json.parse(bodyText).as[Map[String, JsValue]]
      error.get("failure") must beSome[JsValue].like { case JsString(msg) => msg mustEqual "No seed was sent." }
    }

    "fail when the seed is invalid" >> {
      val controller = new AdminController(Stubs.stubSecurityComponents(), mock[AccountDao], new FakeAppConfig())
      val result = controller.addAccount().apply(FakeRequest("POST", "/accounts/add")
        .withFormUrlEncodedBody("seed" -> "avocado seed"))
      status(result) mustEqual 400
      val bodyText: String = contentAsString(result)
      val error = Json.parse(bodyText).as[Map[String, JsValue]]
      error.get("failure") must beSome[JsValue].like { case JsString(msg) => msg mustEqual "The seed is invalid." }
    }

    "fail with 500 when there is some unexpected failure to save the account" >> {
      val dao = mock[AccountDao]
      val kp = KeyPair.random
      dao.insert(kp) throws new RuntimeException("something weird")

      val appConfig = new FakeAppConfig()
      appConfig.stubNetwork.expectAccount(kp,
        AccountResponse(kp.asPublicKey, 1, 1, Thresholds(1, 2, 3), authRequired = false, authRevocable = true, Nil, Nil))

      val controller = new AdminController(Stubs.stubSecurityComponents(), dao, appConfig)
      val result = controller.addAccount().apply(FakeRequest("POST", "/accounts/add")
        .withFormUrlEncodedBody("seed" -> kp.secretSeed.mkString))
      status(result) mustEqual 500
      val bodyText: String = contentAsString(result)
      val error = Json.parse(bodyText).as[Map[String, JsValue]]
      error.get("error") must beSome[JsValue].like { case JsString(msg) => msg mustEqual "something weird" }
    }

    "fail when the seed does not represent a valid account on the network" >> {
      val dao = mock[AccountDao]
      val kp = KeyPair.random
      dao.insert(kp) throws new RuntimeException("something weird")
      val controller = new AdminController(Stubs.stubSecurityComponents(), dao, new FakeAppConfig())
      val result = controller.addAccount().apply(FakeRequest()
        .withFormUrlEncodedBody("seed" -> kp.secretSeed.mkString))
      status(result) mustEqual 400
      val bodyText: String = contentAsString(result)
      val error = Json.parse(bodyText).as[Map[String, JsValue]]
      error.get("failure") must beSome[JsValue].like { case JsString(msg) =>
        msg mustEqual s"The account does not exist."
      }
    }

    "fail when the account is already in the database" >> {
      val dao = mock[AccountDao]
      val kp = KeyPair.random
      dao.insert(kp) throws new SQLException("key violation", "23505", 23505)
      val config = new FakeAppConfig()
      config.stubNetwork.expectAccount(kp,
        AccountResponse(kp.asPublicKey, 1, 1, Thresholds(1, 2, 3), authRequired = false, authRevocable = true, Nil, Nil))
      val controller = new AdminController(Stubs.stubSecurityComponents(), dao, config)
      val result = controller.addAccount().apply(FakeRequest()
        .withFormUrlEncodedBody("seed" -> kp.secretSeed.mkString))
      status(result) mustEqual 400
      val bodyText: String = contentAsString(result)
      val error = Json.parse(bodyText).as[Map[String, JsValue]]
      error.get("failure") must beSome[JsValue].like { case JsString(msg) =>
        msg mustEqual s"The account already exists in the database."
      }
    }

    "return the account id when the inserts succeed" >> {
      val dao = mock[AccountDao]
      val kp = KeyPair.random
      val config = new FakeAppConfig()
      config.stubNetwork.expectAccount(kp,
        AccountResponse(kp.asPublicKey, 1, 1, Thresholds(1, 2, 3), authRequired = false, authRevocable = true, Nil, Nil))
      val controller = new AdminController(Stubs.stubSecurityComponents(), dao, config)
      val result = controller.addAccount().apply(FakeRequest()
        .withFormUrlEncodedBody("seed" -> kp.secretSeed.mkString))
      status(result) mustEqual 200
      val bodyText: String = contentAsString(result)
      val success = Json.parse(bodyText).as[Map[String, JsValue]]
      success.get("account") must beSome[JsValue].like { case JsString(msg) =>
        msg mustEqual kp.accountId
      }
    }
  }

}
