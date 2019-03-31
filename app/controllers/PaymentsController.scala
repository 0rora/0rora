package controllers

import javax.inject._
import models.Payment
import models.repo.PaymentRepo
import org.pac4j.core.profile.CommonProfile
import org.pac4j.play.scala.{Security, SecurityComponents}
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Json, Writes}
import play.api.mvc._

@Singleton
class PaymentsController @Inject()(val controllerComponents: SecurityComponents,
                                   paymentRepo: PaymentRepo) extends BaseController with Security[CommonProfile] {


  private val stroopsInLumen = 10000000.0

  private val paymentFields = (p: Payment) =>
    Some((
      p.id,
      p.scheduled.toInstant.toEpochMilli,
      p.submitted.map(_.toInstant.toEpochMilli),
      p.source.account,
      p.destination.account,
      p.code,
      p.units / stroopsInLumen,
      p.status.name,
      p.opResult,
      p.sourceResolved.map(_.accountId),
      p.destinationResolved.map(_.accountId)
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
    (JsPath \ "result").writeNullable[String] and
    (JsPath \ "from_res").writeNullable[String] and
    (JsPath \ "to_res").writeNullable[String]
    )(unlift(paymentFields))

  implicit val paymentsWrites: Writes[PaymentSubList] = (
    (JsPath \ "payments").write[Seq[Payment]] and
    (JsPath \ "total").write[Option[Int]]
  )(unlift(PaymentSubList.unapply))

  def listHistory(): Action[AnyContent] = Secure("FormClient") { implicit req =>
    val payments = paymentRepo.history()
    val count = paymentRepo.countHistoric
    Ok(Json.toJson(PaymentSubList(payments, Some(count))))
  }

  def listHistoryBefore(id: Long): Action[AnyContent] = Secure("FormClient") { implicit req =>
    val payments = paymentRepo.historyBefore(id)
    Ok(Json.toJson(PaymentSubList(payments)))
  }

  def listHistoryAfter(id: Long): Action[AnyContent] = Secure("FormClient") { implicit req =>
    val payments = paymentRepo.historyAfter(id)
    Ok(Json.toJson(PaymentSubList(payments.reverse)))
  }

  def listScheduled(): Action[AnyContent] = Secure("FormClient") { implicit req =>
    val payments = paymentRepo.scheduled()
    val count = paymentRepo.countScheduled
    Ok(Json.toJson(PaymentSubList(payments, Some(count))))
  }

  def listScheduledBefore(id: Long): Action[AnyContent] = Secure("FormClient") { implicit req =>
    val payments = paymentRepo.scheduledBefore(id)
    Ok(Json.toJson(PaymentSubList(payments.reverse)))
  }

  def listScheduledAfter(id: Long): Action[AnyContent] = Secure("FormClient") { implicit req =>
    val payments = paymentRepo.scheduledAfter(id)
    Ok(Json.toJson(PaymentSubList(payments)))
  }
}

case class PaymentSubList(payments: Seq[Payment], total: Option[Int] = None)

