package controllers

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Sink, Source}
import javax.inject.Inject
import kantan.csv.ops._
import kantan.csv.{rfc, _}
import models.PaymentProcessor
import models.repo.{Payment, PaymentRepo}
import play.api.Configuration
import play.api.libs.Files
import play.api.libs.json.Json
import play.api.mvc._
import stellar.sdk.KeyPair

import scala.concurrent.ExecutionContext.Implicits.global

class SourcesController @Inject()(cc: MessagesControllerComponents,
                                  config: Configuration,
                                  paymentRepo: PaymentRepo,
                                  paymentProcessor: PaymentProcessor,
                                  implicit val system: ActorSystem) extends MessagesAbstractController(cc) {

  implicit private val mat: ActorMaterializer = ActorMaterializer()
  implicit private val paymentDecoder: RowDecoder[Payment] = RowDecoder.ordered {
    (sender: String,
     destination: String,
     asset: String,
     issuer: Option[String],
     units: Long,
     schedule: Option[String]) =>
      Payment(
        None,
        KeyPair.fromAccountId(sender),
        KeyPair.fromAccountId(destination),
        asset,
        issuer.map(KeyPair.fromAccountId),
        units,
        ZonedDateTime.now,
        schedule.map(ZonedDateTime.parse(_, ISO_OFFSET_DATE_TIME)).getOrElse(ZonedDateTime.now),
        None,
        Payment.Pending
      )
  }

  private def countingSink[T] = Sink.fold[Int, T](0)((acc, _) => acc + 1)

  def uploadCSV: Action[MultipartFormData[Files.TemporaryFile]] = Action(parse.multipartFormData).async { request =>
    val path = request.body.files.head.ref.path
    def iter = path.asCsvReader[Payment](rfc.withoutHeader).collect { case Right(op) => op }.toIterator
      .filter(p => p.issuer.nonEmpty || p.code == "XLM")

    val (count, _) = Source.fromIterator(() => iter)
      .alsoToMat(countingSink)(Keep.right)
      .toMat(paymentRepo.writer)(Keep.both)
      .run()

    count.map{i =>
      paymentProcessor.checkForPayments()
      Ok(Json.obj("success" -> true, "count" -> i))
    }
  }

}
