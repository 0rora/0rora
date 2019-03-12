package models

import java.time.ZonedDateTime

import stellar.sdk.PublicKeyOps
import stellar.sdk.model.{Asset, IssuedAmount, NativeAmount}
import stellar.sdk.model.op.PaymentOperation

import scala.concurrent.{ExecutionContext, Future}

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
                   opResult: Option[String] = None) {

  def asOperation()(implicit ec: ExecutionContext): Future[PaymentOperation] = for {
    s <- source.pk()
    d <- destination.pk()
  } yield PaymentOperation(
    d, issuer.map(i => IssuedAmount(units, Asset(code, i))).getOrElse(NativeAmount(units)), Some(s)
  )
}

object Payment {

  def status(s: String): Status = s match {
    case "pending" => Pending
    case "submitted" => Submitted
    case "failed" => Failed
    case "succeeded" => Succeeded
    case _ => throw new Exception(s"Payment status unrecognised: $s")
  }

  sealed trait Status {
    val name: String = getClass.getSimpleName.toLowerCase().replace("$", "")
  }

  case object Pending extends Status

  case object Submitted extends Status

  case object Failed extends Status

  case object Succeeded extends Status

}
