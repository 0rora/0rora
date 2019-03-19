package models

import org.specs2.mutable.Specification
import play.api.inject.{Binding, BindingKey, BindingTarget, ProviderTarget}
import play.api.{Configuration, Environment}
import scalikejdbc.{AutoSession, DBSession, SettingsProvider}

class PaymentProcessorModuleSpec extends Specification {

  "the processor module" should {
    "bind instances for dependency injection" >> {
      val bindings = new PaymentProcessorModule().bindings(Environment.simple(), Configuration.empty)
      bindings.size mustEqual 2
      val Seq(a, b) = bindings
      a must beLike[Binding[_]] { case binding =>
        binding.key mustEqual BindingKey(classOf[PaymentProcessor])
        binding.eager must beTrue
      }
      b must beLike[Binding[_]] { case binding =>
        binding.key mustEqual BindingKey(classOf[DBSession])
        binding.eager must beFalse
        binding.target must beSome[BindingTarget[_]].like {
          case ProviderTarget(p) => p.get() mustEqual AutoSession
        }
      }
    }
  }

}
