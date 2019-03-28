package models

import org.scalacheck.Arbitrary
import org.specs2.ScalaCheck
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import stellar.sdk.model.NativeAmount
import stellar.sdk.model.op.PaymentOperation

class PaymentSpec(implicit ee: ExecutionEnv) extends Specification with ScalaCheck {

  implicit val arb: Arbitrary[Payment] = Arbitrary(Generators.genValidatedPayment)

  "a payment" should {
    "be convertible to a payment operation" >> prop { p: Payment =>
      p.asOperation must beSome[PaymentOperation].like { case op: PaymentOperation =>
        op.amount mustEqual NativeAmount(p.units)
        op.sourceAccount mustEqual p.sourceResolved
        op.destinationAccount mustEqual p.destinationResolved.get
      }
    }

    "not convert if the source is not resolved" >> prop { p: Payment =>
      p.copy(sourceResolved = None).asOperation must beNone
    }

    "not convert if the destination is not resolved" >> prop { p: Payment =>
      p.copy(destinationResolved = None).asOperation must beNone
    }
  }

  "a payment status" should {
    "be interpreted from a string" >> {
      Payment.status("pending") mustEqual Payment.Pending
      Payment.status("invalid") mustEqual Payment.Invalid
      Payment.status("submitted") mustEqual Payment.Submitted
      Payment.status("failed") mustEqual Payment.Failed
      Payment.status("succeeded") mustEqual Payment.Succeeded
    }

    "fail to be interpreted when string is not recognised" >> prop { s: String =>
      (Payment.status(s) must throwAn[Exception]).unless(
        Set("pending", "invalid", "submitted", "failed", "succeeded").contains(s)
      )
    }
  }

}
