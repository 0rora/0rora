package controllers

import java.util.Locale

import akka.actor.ActorSystem
import controllers.actions.AuthenticatedUserAction
import javax.inject._
import models.PaymentProcessor
import models.repo.{Payment, PaymentRepo}
import play.api.Configuration
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Json, Writes}
import play.api.mvc.{Action, AnyContent, MessagesAbstractController, MessagesControllerComponents}
import stellar.sdk.model.result._

@Singleton
class PaymentsController @Inject()(cc: MessagesControllerComponents,
                                   authenticatedUserAction: AuthenticatedUserAction,
                                   paymentRepo: PaymentRepo,
                                   processor: PaymentProcessor,
                                   config: Configuration,
                                   system: ActorSystem
                                  ) extends MessagesAbstractController(cc) {

  private val stroopsInLumen = 10000000.0

  private val paymentResultByCode: Map[Int, PaymentResult] = Seq(
    PaymentSuccess, PaymentMalformed, PaymentUnderfunded, PaymentSourceNoTrust, PaymentSourceNotAuthorised,
    PaymentNoDestination, PaymentDestinationNoTrust, PaymentDestinationNotAuthorised, PaymentDestinationLineFull, PaymentNoIssuer
  ).map(r => r.opResultCode -> r).toMap

  private val paymentFields = (p: Payment) =>
    Some((
      p.scheduled.toInstant.toEpochMilli,
      p.source.accountId,
      p.destination.accountId,
      p.code,
      p.units / stroopsInLumen,
      p.status.name,
      p.opResultCode.flatMap(paymentResultByCode.get).map(_.toString)
    ))

  implicit val paymentWrites: Writes[Payment] = (
    (JsPath \ "date").write[Long] and
    (JsPath \ "from").write[String] and
    (JsPath \ "to").write[String] and
    (JsPath \ "asset").write[String] and
    (JsPath \ "units").write[Double] and
    (JsPath \ "status").write[String] and
    (JsPath \ "result").writeNullable[String]
    )(unlift(paymentFields))

  def listHistory: Action[AnyContent] = authenticatedUserAction { implicit req =>
    Ok(Json.toJson(paymentRepo.listHistoric))
  }

  def listScheduled: Action[AnyContent] = authenticatedUserAction { implicit req =>
    Ok(Json.toJson(paymentRepo.listScheduled))
  }
}