package models

import java.time.ZonedDateTime

import stellar.sdk.PublicKeyOps
import stellar.sdk.model.op.PaymentOperation
import stellar.sdk.model.{Asset, IssuedAmount, NativeAmount}

case class Payment(id: Option[Long],
                   source: AccountIdLike,
                   destination: AccountIdLike,
                   code: String,
                   issuer: Option[PublicKeyOps],
                   units: Long,
                   received: ZonedDateTime,
                   scheduled: ZonedDateTime,
                   submitted: Option[ZonedDateTime],
                   status: Payment.Status,
                   opResult: Option[String] = None,
                   sourceResolved: Option[PublicKeyOps] = None,
                   destinationResolved: Option[PublicKeyOps] = None) {

  def asOperation: Option[PaymentOperation] = for {
    s <- sourceResolved
    d <- destinationResolved
  } yield PaymentOperation(
    d, issuer.map(i => IssuedAmount(units, Asset(code, i))).getOrElse(NativeAmount(units)), Some(s)
  )
}

object Payment {

  def status(s: String): Status = s match {
    case "pending" => Pending
    case "validating" => Validating
    case "invalid" => Invalid
    case "valid" => Valid
    case "submitted" => Submitted
    case "failed" => Failed
    case "succeeded" => Succeeded
    case _ => throw new Exception(s"Payment status unrecognised: $s")
  }

  /**
    * State transition for payments:
    *
    * +-----------+
    * |  pending  |
    * +-----+-----+
    *       |
    *       |
    * +-----v------+   +-----------+
    * | validating +--->  invalid  |
    * +-----+------+   +-----------+
    *       |
    *       |
    * +-----v-----+
    * |  valid    |
    * +-----+-----+
    *       |
    *       |
    * +-----v-----+   +-----------+
    * | submitted +--->  failed   |
    * +-----+-----+   +-----------+
    *       |
    *       |
    * +-----v-----+
    * | succeeded |
    * +-----------+
    *
    */
  sealed trait Status {
    val name: String = getClass.getSimpleName.toLowerCase().replace("$", "")
  }

  case object Pending extends Status
  case object Validating extends Status
  case object Invalid extends Status
  case object Valid extends Status
  case object Submitted extends Status
  case object Failed extends Status
  case object Succeeded extends Status

}
