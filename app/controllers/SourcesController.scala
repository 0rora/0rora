package controllers

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import controllers.flows.PaymentFlow
import javax.inject.Inject
import kantan.csv.ops._
import kantan.csv.{rfc, _}
import play.api.libs.Files
import play.api.mvc._
import stellar.sdk.op.PaymentOperation
import stellar.sdk.{Amount, Asset, KeyPair, NativeAmount}

class SourcesController @Inject()(cc: MessagesControllerComponents, implicit val system: ActorSystem) extends MessagesAbstractController(cc) {

  implicit private val mat: ActorMaterializer = ActorMaterializer()
  implicit private val paymentDecoder: RowDecoder[PaymentOperation] = RowDecoder.ordered {
    (s: String, d: String, c: String, i: Option[String], u: Long) =>
      PaymentOperation(
        KeyPair.fromAccountId(d),
        i match {
          case None if c == "XLM" => NativeAmount(u)
          case Some(issuer) => Amount(u, Asset(c, KeyPair.fromAccountId(issuer)))
          case _ => throw new Exception(s"Invalid asset without issuer: $c")
        },
        Some(KeyPair.fromAccountId(s))
      )
  }

  def uploadCSV: Action[MultipartFormData[Files.TemporaryFile]] = Action(parse.multipartFormData) { request =>
    val path = request.body.files.head.ref.path
    def iter = path.asCsvReader[PaymentOperation](rfc.withoutHeader).collect { case Right(op) => op }.toIterator
      Source.fromIterator(() => iter)
        .to(PaymentFlow.sink)
        .run()
      Ok("""{"msg":"File processing","success":true}""")
  }

}
