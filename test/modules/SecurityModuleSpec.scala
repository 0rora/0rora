package modules

import java.lang.annotation.Annotation
import java.lang.reflect.Method
import java.sql.Connection

import com.google.inject._
import com.google.inject.binder.{AnnotatedBindingBuilder, AnnotatedConstantBindingBuilder, LinkedBindingBuilder}
import com.google.inject.matcher.Matcher
import com.google.inject.spi._
import models.PaymentProcessor
import org.aopalliance.intercept.MethodInterceptor
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers._
import org.pac4j.http.client.indirect.FormClient
import org.pac4j.play.LogoutController
import org.pac4j.play.http.PlayHttpActionAdapter
import org.pac4j.play.scala.SecurityComponents
import org.pac4j.play.store.PlaySessionStore
import org.pac4j.sql.profile.service.DbProfileService
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import play.api.inject.{Binding, BindingKey, BindingTarget, ProviderTarget}
import play.api.{Configuration, Environment}
import scalikejdbc.DBSession

import scala.collection.JavaConverters._
import scala.util.Try

class SecurityModuleSpec extends Specification with Mockito {

  "the security module" should {
    "provide security config" >> {
      val formClient = mock[FormClient]
      formClient.getName returns "MockClient"
      val c = new SecurityModule().provideConfig(formClient)
      c.getClients.findAllClients().asScala mustEqual List(formClient)
      c.getHttpActionAdapter must haveClass[PlayHttpActionAdapter]
    }

    "provide an auth client" >> {
      val authenticator = mock[DbProfileService]
      val c = new SecurityModule().provideFormClient(authenticator)
      c.getLoginUrl mustEqual "/login"
      c.getAuthenticator mustEqual authenticator
    }

    "provide an authenticator" >> {
      val authenticator = new SecurityModule().provideAuthenticator(Configuration(
        "db.default.driver" -> "org.h2.Driver",
        "db.default.url" -> "jdbc:h2:mem:",
        "db.default.username" -> "",
        "db.default.password" -> ""
      ))

      authenticator must haveClass[DbProfileService]
      Try(authenticator.getDataSource.getConnection) must beASuccessfulTry[Connection]
    }

    "provide and encrypter" >> {
      val s = "He felt that his whole life was some kind of dream and he sometimes wondered whose it was and whether they were enjoying it."
      val encrypter = new SecurityModule().provideEncrypter(Configuration(
        "play.http.secret.key" -> "Time is an illusion. Lunchtime doubly so."
      ))
      new String(encrypter.decrypt(encrypter.encrypt(s.getBytes("UTF-8"))), "UTF-8") mustEqual s
    }

    "configure the app" >> {
      val module = new SecurityModule()
      val bindings = module.bindings(Environment.simple(), Configuration(
        "db.default.driver" -> "org.h2.Driver",
        "db.default.url" -> "jdbc:h2:mem:",
        "db.default.username" -> "",
        "db.default.password" -> "",
        "play.http.secret.key" -> "Time is an illusion. Lunchtime doubly so."
      ))

      bindings.size mustEqual 3
      val Seq(a, b, c) = bindings
      a must beLike[Binding[_]] { case binding =>
        binding.key mustEqual BindingKey(classOf[PlaySessionStore])
      }
      b must beLike[Binding[_]] { case binding =>
        binding.key mustEqual BindingKey(classOf[SecurityComponents])
      }
      c must beLike[Binding[_]] { case binding =>
        binding.key mustEqual BindingKey(classOf[LogoutController])
      }
    }
  }

}
