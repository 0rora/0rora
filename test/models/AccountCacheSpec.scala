package models

import org.scalacheck.Arbitrary
import org.specs2.ScalaCheck
import org.specs2.mutable.Specification
import stellar.sdk.model.Account

class AccountCacheSpec extends Specification with ScalaCheck {

  implicit val arb: Arbitrary[Account] = Arbitrary(Generators.genAccount)

  "an account cache" should {
    "permit an account to be borrowed only once" >> prop { a: Account =>
      val cache = new AccountCache
      cache.readyCount mustEqual 0
      cache.returnAccount(a)
      cache.readyCount mustEqual 1
      cache.borrowAccount must beSome(a)
      cache.readyCount mustEqual 0
      cache.borrowAccount must beNone
    }

    "disallow an account to be borrowed once retired" >> prop { a: Account =>
      val cache = new AccountCache
      cache.readyCount mustEqual 0
      cache.returnAccount(a)
      cache.readyCount mustEqual 1
      cache.retireAccount(a)
      cache.readyCount mustEqual 0
      cache.borrowAccount must beNone
      cache.readyCount mustEqual 0
    }

    "allow the borrowing of multiple accounts" >> prop { as: Seq[Account] =>
      val cache = new AccountCache
      as.foreach(cache.returnAccount)
      val borrowed = Stream.continually(cache.borrowAccount).takeWhile(_.isDefined).flatten
      borrowed must containTheSameElementsAs(as)
    }

    "return an account even after it has been modified" >> prop { a: Account =>
      val cache = new AccountCache
      cache.returnAccount(a)
      cache.borrowAccount
      val b = a.withIncSeq
      cache.returnAccount(b)
      cache.borrowAccount must beSome(b)
    }
  }

}
