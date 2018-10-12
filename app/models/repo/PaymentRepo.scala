package models.repo

import java.time.{Instant, ZoneId, ZonedDateTime}

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Sink}
import javax.inject.Inject
import scalikejdbc.{AutoSession, _}
import stellar.sdk.{KeyPair, PublicKeyOps}

import scala.concurrent.duration._

@javax.inject.Singleton
class PaymentRepo @Inject()() {
  implicit private val session: AutoSession = AutoSession
  private val UTC = ZoneId.of("UTC")

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
      values (?, ?, ?, ?, ?, ?, ?, cast(? as payment_status))
    """.batch(params: _*).apply()
  })

  def list: Seq[Payment] = {
    sql"""
       select id, source, destination, code, issuer, units, received, scheduled, status
       from payments
       order by id
    """.map(from).list().apply()
  }

  def due: Seq[Payment] = {
    sql"""
       select id, source, destination, code, issuer, units, received, scheduled, status
       from payments
       where status='pending'
       and scheduled <= ${ZonedDateTime.now.toInstant}
    """.map(from).list().apply()
  }

  def submit(ids: Seq[Long]): Unit = {
    sql"""
        update payments set status='submitted' where id in ($ids)
    """.update().apply()
  }

  def durationUntilNextDue: Option[FiniteDuration] = {
    sql"""select min(scheduled) as next from payments where status='pending'""".map {rs =>
      Option(rs.timestamp("next")).map { next =>
        val when = ZonedDateTime.ofInstant(next.toInstant, UTC)
        val now = ZonedDateTime.now
        Duration.fromNanos(math.max(0L, java.time.Duration.between(now.toInstant, when.toInstant).toNanos))
      }
    }.single().apply().flatten
  }

  private def from(rs: WrappedResultSet): Payment =
    Payment(
      id = rs.longOpt("id"),
      source = KeyPair.fromAccountId(rs.string("source")),
      destination = KeyPair.fromAccountId(rs.string("destination")),
      code = rs.string("code"),
      issuer = rs.stringOpt("issuer").map(KeyPair.fromAccountId),
      units = rs.long("units"),
      received = ZonedDateTime.ofInstant(rs.timestamp("received").toInstant, UTC),
      scheduled = ZonedDateTime.ofInstant(rs.timestamp("scheduled").toInstant, UTC),
      status = Payment.status(rs.string("status"))
    )
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

  def status(s: String): Status = s match {
    case "pending" => Pending
    case "submitted" => Submitted
    case "failed" => Failed
    case "succeeded" => Succeeded
    case _ => throw new Exception(s"Payment status unrecognised: $s")
  }

  sealed trait Status

  case object Pending extends Status

  case object Submitted extends Status

  case object Failed extends Status

  case object Succeeded extends Status

}


