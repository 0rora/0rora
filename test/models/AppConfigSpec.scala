package models

import java.net.URI

import org.scalacheck.Arbitrary
import org.specs2.ScalaCheck
import org.specs2.mutable.Specification
import play.api.Configuration
import stellar.sdk.{PublicNetwork, StandaloneNetwork, TestNetwork}

import scala.util.Try

class AppConfigSpec extends Specification with ScalaCheck {

  "the app config" should {
    "parse the 0rora.horizon value 'test' to the TestNetwork" >> {
      val conf = new AppConfig(Configuration("0rora.horizon" -> "test", "0rora.accounts" -> Seq.empty[String]))
      conf.network mustEqual TestNetwork
    }

    "parse the 0rora.horizon value 'public' to the PublicNetwork" >> {
      val conf = new AppConfig(Configuration("0rora.horizon" -> "public", "0rora.accounts" -> Seq.empty[String]))
      conf.network mustEqual PublicNetwork
    }

    "parse a URI-like 0rora.horizon value to a StandaloneNetwork" >> prop { s: String =>
      val conf = new AppConfig(Configuration("0rora.horizon" -> s, "0rora.accounts" -> Seq.empty[String]))
      conf.network mustEqual StandaloneNetwork(new URI(s))
    }.setArbitrary(Arbitrary(Generators.genURL))

    "fail to parse any other string to a network" >> prop { s: String =>
      new AppConfig(Configuration("0rora.horizon" -> s, "0rora.accounts" -> Seq.empty[String])) must
        throwAn[InvalidConfig].unless(s == "test" || s == "public" || Try(new URI(s).toURL).isSuccess)
    }
  }

}
