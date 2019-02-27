package models

import org.scalacheck.Arbitrary
import org.specs2.ScalaCheck
import org.specs2.mutable.Specification
import stellar.sdk.model.NativeAmount
import stellar.sdk.model.op.PaymentOperation

class PaymentSpec extends Specification with ScalaCheck {

  implicit val arb: Arbitrary[Payment] = Arbitrary(Generators.genPayment)

  "a payment" should {
    "be convertible to a payment operation" >> prop { p: Payment =>
      p.asOperation must beLike[PaymentOperation] { case op =>
        op.amount mustEqual NativeAmount(p.units)
        op.destinationAccount mustEqual p.destination
        op.sourceAccount must beSome(p.source)
      }
    }
  }

  "a payment status" should {
    "be interpreted from a string" >> {
      Payment.status("pending") mustEqual Payment.Pending
      Payment.status("submitted") mustEqual Payment.Submitted
      Payment.status("failed") mustEqual Payment.Failed
      Payment.status("succeeded") mustEqual Payment.Succeeded
    }

    "fail to be interpreted when string is not recognised" >> prop { s: String =>
      (Payment.status(s) must throwAn[Exception]).unless(Set("pending", "submitted", "failed", "succeeded").contains(s))
    }
  }

}
