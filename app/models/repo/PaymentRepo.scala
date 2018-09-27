package models.repo

import java.time.ZonedDateTime

import akka.{Done, NotUsed}
import akka.stream.scaladsl.{Flow, Sink}
import javax.inject.Inject
import play.api.db.Database
import scalikejdbc.AutoSession
import stellar.sdk.PublicKeyOps
import scalikejdbc._

import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class PaymentRepo() {
  implicit private val session: AutoSession = AutoSession

  val writer: Sink[Payment, NotUsed] = Flow[Payment]
    .groupedWithin(100, 1.second)
    .map {
      _.map(p =>
        Seq(p.source.accountId, p.destination.accountId, p.code, p.issuer.orNull, p.units, p.received, p.scheduled,
          p.status.toString.toLowerCase)
      )
    }.to(Sink.foreach { params =>
    sql"""
      insert into payments (source, destination, code, issuer, units, received, scheduled, status)
      values (?, ?, ?, ?, ?, ?, ?, ?::payment_status)
    """.batch(params: _*).apply()
  })
}

case class Payment(id: Option[Long],
                   source: PublicKeyOps,
                   destination: PublicKeyOps,
                   code: String,
                   issuer: Option[PublicKeyOps],
                   units: Long,
                   received: ZonedDateTime,
                   scheduled: ZonedDateTime,
                   status: Payment.Status)

object Payment {

  sealed trait Status

  case object Pending extends Status

  case object Submitted extends Status

  case object Failed extends Status

  case object Succeeded extends Status

}


