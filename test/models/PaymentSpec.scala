package models

import org.scalacheck.Arbitrary
import org.specs2.ScalaCheck
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import stellar.sdk.model.NativeAmount

import scala.concurrent.Await
import scala.concurrent.duration._

class PaymentSpec(implicit ee: ExecutionEnv) extends Specification with ScalaCheck {

  implicit val arb: Arbitrary[Payment] = Arbitrary(Generators.genPayment)

  "a payment" should {
    "be convertible to a payment operation" >> prop { p: Payment =>
      val op = Await.result(p.asOperation(), 30 seconds)
      op.amount mustEqual NativeAmount(p.units)
      op.destinationAccount mustEqual Await.result(p.destination.pk(), 5 seconds)
      op.sourceAccount must beSome(Await.result(p.source.pk(), 5 seconds))
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
