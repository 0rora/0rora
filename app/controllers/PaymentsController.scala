package controllers

import akka.actor.ActorSystem
import controllers.actions.AuthenticatedUserAction
import javax.inject._
import models.PaymentProcessor
import models.repo.{Payment, PaymentRepo}
import play.api.Configuration
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Json, Writes}
import play.api.mvc.{Action, AnyContent, MessagesAbstractController, MessagesControllerComponents}

@Singleton
class PaymentsController @Inject()(cc: MessagesControllerComponents,
                                   authenticatedUserAction: AuthenticatedUserAction,
                                   paymentRepo: PaymentRepo,
                                   processor: PaymentProcessor,
                                   config: Configuration,
                                   system: ActorSystem
                                  ) extends MessagesAbstractController(cc) {

  private val paymentFields = (p: Payment) =>
    Some((
      p.scheduled.toInstant.toEpochMilli,
      p.source.accountId,
      shortAccountId(p.source.accountId),
      p.destination.accountId,
      shortAccountId(p.destination.accountId),
      p.code,
      p.units,
      p.status.name
    ))

  implicit val paymentWrites: Writes[Payment] = (
    (JsPath \ "date").write[Long] and
    (JsPath \ "from").write[String] and
    (JsPath \ "from_short").write[String] and
    (JsPath \ "to").write[String] and
    (JsPath \ "to_short").write[String] and
    (JsPath \ "asset").write[String] and
    (JsPath \ "units").write[Long] and
    (JsPath \ "status").write[String]
    )(unlift(paymentFields))

  def listHistory: Action[AnyContent] = authenticatedUserAction { implicit req =>
    Ok(Json.toJson(paymentRepo.listHistoric))
  }

  def listScheduled: Action[AnyContent] = authenticatedUserAction { implicit req =>
    Ok(Json.toJson(paymentRepo.listScheduled))
  }

  private def shortAccountId(s: String): String = s.take(4) + "…" + s.drop(50)
}