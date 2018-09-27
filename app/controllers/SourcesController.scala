package controllers

import java.time.ZonedDateTime

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import controllers.flows.PaymentFlow
import javax.inject.Inject
import kantan.csv.ops._
import kantan.csv.{rfc, _}
import models.repo.{Payment, PaymentRepo}
import play.api.Configuration
import play.api.libs.Files
import play.api.mvc._
import stellar.sdk.op.PaymentOperation
import stellar.sdk.{Amount, Asset, KeyPair, NativeAmount}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class SourcesController @Inject()(cc: MessagesControllerComponents,
                                  config: Configuration,
                                  paymentRepo: PaymentRepo,
                                  implicit val system: ActorSystem) extends MessagesAbstractController(cc) {

  implicit private val mat: ActorMaterializer = ActorMaterializer()
  implicit private val paymentDecoder: RowDecoder[Payment] = RowDecoder.ordered {
    (s: String, d: String, c: String, i: Option[String], u: Long) =>
      Payment(
        None,
        KeyPair.fromAccountId(s),
        KeyPair.fromAccountId(d),
        c,
        i.map(KeyPair.fromAccountId),
        u,
        ZonedDateTime.now,
        ZonedDateTime.now,
        Payment.Pending
      )
  }

  def uploadCSV: Action[MultipartFormData[Files.TemporaryFile]] = Action(parse.multipartFormData) { request =>
    val path = request.body.files.head.ref.path
    def iter = path.asCsvReader[Payment](rfc.withoutHeader).collect { case Right(op) => op }.toIterator
      Source.fromIterator(() => iter)
      .to(paymentRepo.writer)
        .run()
      Ok("""{"msg":"File processing","success":true}""")
  }

}
