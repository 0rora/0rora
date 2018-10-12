package controllers

import akka.actor.{ActorSystem, Props}
import controllers.actions.AuthenticatedUserAction
import javax.inject._
import models.{CheckForPayments, PaymentProcessor}
import models.repo.{Payment, PaymentRepo}
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Json, Writes}
import play.api.mvc.{MessagesAbstractController, MessagesControllerComponents}

@Singleton
class PaymentsController @Inject()(cc: MessagesControllerComponents,
                                   authenticatedUserAction: AuthenticatedUserAction,
                                   paymentRepo: PaymentRepo,
                                   system: ActorSystem
                                  ) extends MessagesAbstractController(cc) {

  private val paymentProcessor = system.actorOf(Props(classOf[PaymentProcessor], paymentRepo))
  paymentProcessor ! CheckForPayments

  private val paymentFields = (p: Payment) =>
    Some((p.scheduled.toString, p.source.accountId, p.destination.accountId, p.code, p.units))

  implicit val placeWrites: Writes[Payment] = (
    (JsPath \ "date").write[String] and
    (JsPath \ "from").write[String] and
    (JsPath \ "to").write[String] and
    (JsPath \ "asset").write[String] and
    (JsPath \ "units").write[Long]
    )(unlift(paymentFields))

  def list() = authenticatedUserAction { implicit req =>
    Ok(Json.toJson(paymentRepo.list))
  }
}