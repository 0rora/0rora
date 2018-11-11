package controllers

import akka.actor.ActorSystem
import controllers.actions.AuthenticatedUserAction
import javax.inject._
import models.PaymentProcessor
import models.repo.Payment.Succeeded
import models.repo.{Payment, PaymentRepo}
import play.api.Configuration
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Json, Writes}
import play.api.mvc.{MessagesAbstractController, MessagesControllerComponents}

@Singleton
class PaymentsController @Inject()(cc: MessagesControllerComponents,
                                   authenticatedUserAction: AuthenticatedUserAction,
                                   paymentRepo: PaymentRepo,
                                   processor: PaymentProcessor,
                                   config: Configuration,
                                   system: ActorSystem
                                  ) extends MessagesAbstractController(cc) {

  processor.checkForPayments("Initial")

  private val paymentFields = (p: Payment) =>
    Some((p.scheduled.toInstant.toEpochMilli, p.source.accountId, p.destination.accountId, p.code, p.units))

  implicit val placeWrites: Writes[Payment] = (
    (JsPath \ "date").write[Long] and
    (JsPath \ "from").write[String] and
    (JsPath \ "to").write[String] and
    (JsPath \ "asset").write[String] and
    (JsPath \ "units").write[Long]
    )(unlift(paymentFields))

  def listSucceeded = authenticatedUserAction { implicit req =>
    Ok(Json.toJson(paymentRepo.list(Succeeded)))
  }
}