package controllers

import java.time.ZoneId

import controllers.actions.AuthenticatedUserAction
import javax.inject._
import models.repo.{Payment, PaymentRepo}
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Json, Writes}
import play.api.mvc.{Action, AnyContent, MessagesAbstractController, MessagesControllerComponents}
import stellar.sdk.model.result._

@Singleton
class PaymentsController @Inject()(cc: MessagesControllerComponents,
                                   authenticatedUserAction: AuthenticatedUserAction,
                                   paymentRepo: PaymentRepo) extends MessagesAbstractController(cc) {

  private val stroopsInLumen = 10000000.0

  private val paymentResultByCode: Map[Int, PaymentResult] = Seq(
    PaymentSuccess, PaymentMalformed, PaymentUnderfunded, PaymentSourceNoTrust, PaymentSourceNotAuthorised,
    PaymentNoDestination, PaymentDestinationNoTrust, PaymentDestinationNotAuthorised, PaymentDestinationLineFull, PaymentNoIssuer
  ).map(r => r.opResultCode -> r).toMap

  private val UTC: ZoneId = ZoneId.of("UTC")

  private val paymentFields = (p: Payment) =>
    Some((
      p.id,
      p.scheduled.toInstant.toEpochMilli,
      p.submitted.map(_.toInstant.toEpochMilli),
      p.source.accountId,
      p.destination.accountId,
      p.code,
      p.units / stroopsInLumen,
      p.status.name,
      p.opResult
    ))

  implicit val paymentWrites: Writes[Payment] = (
    (JsPath \ "id").write[Option[Long]] and
    (JsPath \ "scheduled").write[Long] and
    (JsPath \ "submitted").writeNullable[Long] and
    (JsPath \ "from").write[String] and
    (JsPath \ "to").write[String] and
    (JsPath \ "asset").write[String] and
    (JsPath \ "units").write[Double] and
    (JsPath \ "status").write[String] and
    (JsPath \ "result").writeNullable[String]
    )(unlift(paymentFields))

  implicit val paymentsWrites: Writes[PaymentSubList] = (
    (JsPath \ "payments").write[Seq[Payment]] and
    (JsPath \ "total").write[Option[Int]]
  )(unlift(PaymentSubList.unapply))

  def listHistory(): Action[AnyContent] = authenticatedUserAction { implicit req =>
    val payments = paymentRepo.history()
    val count = paymentRepo.countHistoric
    Ok(Json.toJson(PaymentSubList(payments, Some(count))))
  }

  // todo -test
  def listHistoryBefore(id: Long): Action[AnyContent] = authenticatedUserAction { implicit req =>
    val payments = paymentRepo.historyBefore(id)
    Ok(Json.toJson(PaymentSubList(payments)))
  }

  // todo -test
  def listHistoryAfter(id: Long): Action[AnyContent] = authenticatedUserAction { implicit req =>
    val payments = paymentRepo.historyAfter(id)
    Ok(Json.toJson(PaymentSubList(payments.reverse)))
  }

  // todo - test
  def listScheduled(): Action[AnyContent] = authenticatedUserAction { implicit req =>
    val payments = paymentRepo.scheduled()
    val count = paymentRepo.countScheduled
    Ok(Json.toJson(PaymentSubList(payments, Some(count))))
  }

  // todo - test
  def listScheduledBefore(id: Long): Action[AnyContent] = authenticatedUserAction { implicit req =>
    val payments = paymentRepo.scheduledBefore(id)
    Ok(Json.toJson(PaymentSubList(payments.reverse)))
  }

  // todo - test
  def listScheduledAfter(id: Long): Action[AnyContent] = authenticatedUserAction { implicit req =>
    val payments = paymentRepo.scheduledAfter(id)
    Ok(Json.toJson(PaymentSubList(payments)))
  }
}

case class PaymentSubList(payments: Seq[Payment], total: Option[Int] = None)

