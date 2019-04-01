package modules

import java.sql.Connection

import org.pac4j.http.client.indirect.FormClient
import org.pac4j.play.http.PlayHttpActionAdapter
import org.pac4j.sql.profile.service.DbProfileService
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import play.api.{Configuration, Environment}

import scala.collection.JavaConverters._
import scala.util.Try

class SecurityModuleSpec extends Specification with Mockito {

  val module = new SecurityModule(Environment.simple(), Configuration(
    "db.default.driver" -> "org.h2.Driver",
    "db.default.url" -> "jdbc:h2:mem:",
    "db.default.username" -> "",
    "db.default.password" -> "",
    "play.http.secret.key" -> "Time is an illusion. Lunchtime doubly so."
  ))

  "the security module" should {
    "provide security config" >> {
      val formClient = mock[FormClient]
      formClient.getName returns "MockClient"
      val c = module.provideConfig(formClient)
      c.getClients.findAllClients().asScala mustEqual List(formClient)
      c.getHttpActionAdapter must haveClass[PlayHttpActionAdapter]
    }

    "provide an auth client" >> {
      val authenticator = mock[DbProfileService]
      val c = module.provideFormClient(authenticator)
      c.getLoginUrl mustEqual "/login"
      c.getAuthenticator mustEqual authenticator
    }

    "provide an authenticator" >> {
      val authenticator = module.provideAuthenticator

      authenticator must haveClass[DbProfileService]
      Try(authenticator.getDataSource.getConnection) must beASuccessfulTry[Connection]
    }

    "provide and encrypter" >> {
      val s = "He felt that his whole life was some kind of dream and he sometimes wondered whose it was and whether they were enjoying it."
      val encrypter = module.provideEncrypter
      new String(encrypter.decrypt(encrypter.encrypt(s.getBytes("UTF-8"))), "UTF-8") mustEqual s
    }
  }

}
