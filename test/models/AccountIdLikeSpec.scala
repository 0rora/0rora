package models

import models.Generators.{genFederatedName, genKeyPair}
import org.scalacheck.Arbitrary
import org.specs2.ScalaCheck
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import stellar.sdk.KeyPair

import scala.concurrent.duration._
import scala.util.Try

class AccountIdLikeSpec(implicit ee: ExecutionEnv) extends Specification with ScalaCheck {

  implicit private val arbKeyPair: Arbitrary[KeyPair] = Arbitrary(genKeyPair)

  "creating a new account id like" should {
    "work from a raw account id" >> prop { kp: KeyPair =>
      AccountIdLike(kp.accountId) mustEqual RawAccountId(kp.accountId)
    }

    "work from a federated address" >> prop { address: String =>
      AccountIdLike(address) mustEqual FederatedAddress(address)
    }.setGen(genFederatedName)

    "reject strings that do not match either" >> prop { something: String =>
      AccountIdLike(something) must throwAn[IllegalArgumentException].unless(
        Try(KeyPair.fromAccountId(something)).isSuccess || something.split("\\*").length == 2
      )
    }
  }

  "public key from an account like" should {

    "exist for raw accounts" >> prop { kp: KeyPair =>
      AccountIdLike(kp.accountId).pk() must beEqualTo(kp.asPublicKey).awaitFor(5 seconds)
    }

    "exist for federated addresses" >>  {
      AccountIdLike(s"jem*keybase.io").pk() must beEqualTo(KeyPair.fromAccountId(
        "GBRAZP7U3SPHZ2FWOJLHPBO3XABZLKHNF6V5PUIJEEK6JEBKGXWD2IIE"
      )).awaitFor(30 seconds)
    }
  }


}
