package models

import models.Generators.genKeyPair
import org.scalacheck.Arbitrary
import org.specs2.ScalaCheck
import org.specs2.mutable.Specification
import stellar.sdk.KeyPair

class AccountIdLikeSpec extends Specification with ScalaCheck {

  implicit private val arbKeyPair: Arbitrary[KeyPair] = Arbitrary(genKeyPair)

  "creating a new account id like" should {
    "work from a raw account id" >> prop { kp: KeyPair =>
      AccountIdLike(kp.accountId) mustEqual RawAccountId(kp.accountId)
    }
  }


}
