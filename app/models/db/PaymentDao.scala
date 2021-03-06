package models.db

import java.time.{ZoneId, ZonedDateTime}

import akka.Done
import akka.stream.scaladsl.{Flow, Keep, Sink}
import javax.inject.Inject
import models.Payment._
import models.{AccountIdLike, Payment}
import scalikejdbc._
import stellar.sdk.KeyPair

import scala.concurrent.Future
import scala.concurrent.duration._

@javax.inject.Singleton
class PaymentDao @Inject()()(implicit val session: DBSession) {

  private val UTC = ZoneId.of("UTC")

  private val paymentFields = sqls"id, source, destination, code, issuer, units, received, scheduled, submitted, " +
    sqls"status, op_result, source_resolved, destination_resolved"
  private val selectPayment = sqls"select $paymentFields"

  val writer: Sink[Payment, Future[Done]] = Flow[Payment]
    .groupedWithin(100, 1.second)
    .map {
      _.map(p =>
        Seq(p.source.account, p.destination.account, p.code, p.issuer.map(_.accountId).orNull, p.units, p.received, p.scheduled,
          p.status.toString.toLowerCase)
      )
    }.toMat(Sink.foreach { params =>
    sql"""
      insert into payments (source, destination, code, issuer, units, received, scheduled, status)
      values (?, ?, ?, ?, ?, ?, ?, ?)
    """.batch(params: _*).apply()
  })(Keep.right)

  def countHistoric: Int = {
    sql"""select count(1) from payments where status in ('failed', 'succeeded', 'invalid')""".map(_.int(1)).single().apply().get
  }

  def countScheduled: Int = {
    sql"""select count(1) from payments where status=${Pending.name}""".map(_.int(1)).single().apply().get
  }

  def due: Iterator[Payment] = {
    val now = ZonedDateTime.now.toInstant
    def statement(implicit session: DBSession): Iterator[Payment] = {
      sql"""
        update payments
        set status='submitted', submitted=$now
        where status='pending'
        and scheduled <= $now
        returning $paymentFields
      """.map(from).iterable().apply().toIterator
    }
    session match {
      case AutoSession => DB.localTx(statement(_))
      case _           => statement(session)
    }
  }

  def updateStatus(ids: Seq[Long], status: Payment.Status): Unit = {
    sql"""
        update payments set status=${status.name} where id in ($ids)
    """.update().apply()
  }

  private def updateStatusWithOpResult(idsWithOpResult: Seq[(Long, String)], status: Payment.Status): Unit = {
    val batchParams: Seq[Seq[Any]] = idsWithOpResult
      .map{ case (id, result) => Seq(status.name, result, id) }

    sql"""
        update payments set status=?, op_result=? where id=?
    """.batch(batchParams: _*).apply()
  }

  def submit(ids: Seq[Long], submittedDate: ZonedDateTime): Unit = {
    sql"""
        update payments set status=${Submitted.name}, submitted=$submittedDate where id in ($ids)
    """.update().apply()
  }

  def confirm(ids: Seq[Long]): Unit =
    updateStatusWithOpResult(ids.map(_ -> "OK"), Succeeded)

  def reject(ids: Seq[Long]): Unit = updateStatus(ids, Failed)

  def rejectWithOpResult(idsWithResults: Seq[(Long, String)]): Unit =
    updateStatusWithOpResult(idsWithResults, Failed)

  def invalidate(id: Long, date: ZonedDateTime): Unit = {
    sql"""
        update payments set status=${Invalid.name}, submitted=$date where id=$id
    """.update().apply()
  }

  def earliestTimeDue: Option[ZonedDateTime] = {
    sql"""select min(scheduled) as next from payments where status='pending'""".map { rs =>
      Option(rs.timestamp("next"))
        .map(_.toInstant)
        .map(ZonedDateTime.ofInstant(_, UTC))
    }.single().apply().flatten
  }

  /**
    * The payments that have previously been processed by the network, ordered from most recent to earlier.
    * @param limit limit the quantity of results
    * @return list of 0 to $$limit payments
    */
  def history(limit: Int = 100): Seq[Payment] = {
    sql"""
      $selectPayment
      FROM payments
      WHERE status IN ('succeeded', 'failed', 'invalid')
      ORDER BY submitted DESC, id DESC
      LIMIT $limit;
    """.map(from).list().apply()
  }

  /**
    * The payments that have previously been processed by the network, prior to the given id,
    * ordered from most recent to earlier.
    * @param id only show the payments preceding this id.
    * @param limit limit the quantity of results
    * @return list of 0 to $$limit payments
    */
  def historyBefore(id: Long, limit: Int = 100): Seq[Payment] = {
    sql"""
      $selectPayment
      FROM payments
      WHERE status IN ('succeeded','failed', 'invalid')
      AND (
        submitted < (SELECT submitted FROM payments WHERE id=$id)
        OR
        (submitted = (SELECT submitted FROM payments WHERE id=$id) AND id < $id)
      )
      ORDER BY submitted DESC, id DESC
      LIMIT $limit;
    """.map(from).list().apply()
  }

  /**
    * The payments that have previously been processed by the network, after the given id,
    * ordered from the earliest to most recent.
    * @param id only show the payments succeeding this id
    * @param limit limit the quantity of results
    * @return list of 0 to $$limit payments
    */
  def historyAfter(id: Long, limit: Int = 100): Seq[Payment] = {
    sql"""
      $selectPayment
      FROM payments
      WHERE status IN ('succeeded','failed', 'invalid')
      AND (
        submitted > (SELECT submitted FROM payments WHERE id=$id)
        OR (submitted = (SELECT submitted FROM payments WHERE id=$id) AND id > $id)
      )
      ORDER BY submitted ASC, id ASC
      LIMIT $limit;
    """.map(from).list().apply()
  }

  /**
    * The payments that yet to be processed by the network, ordered from next due to latest due.
    * @param limit limit the quantity of results
    * @return list of 0 to $$limit payments
    */
  def scheduled(limit: Int = 100): Seq[Payment] = {
    sql"""
      $selectPayment
      FROM payments
      WHERE status='pending'
      ORDER BY scheduled ASC, id ASC
      LIMIT $limit;
    """.map(from).list().apply()
  }

  /**
    * The payments that yet to be processed by the network, ordered from next due to latest due, prior to the given id.
    * @param id only return payments prior to the scheduled date of the given id (or the same date, by lower id).
    * @param limit limit the quantity of results
    * @return list of 0 to $$limit payments
    */
  def scheduledBefore(id: Long, limit: Int = 100): Seq[Payment] = {
    sql"""
       $selectPayment
       FROM payments
       WHERE status='pending'
       AND (
         scheduled < (select scheduled from payments where id=$id)
         OR (scheduled = (select scheduled from payments where id=$id) AND id < $id)
       )
       ORDER BY scheduled DESC, id DESC
       LIMIT $limit;
    """.map(from).list().apply()
  }

  /**
    * The payments that yet to be processed by the network, ordered from next due to latest due, after to the given id.
    * @param id only return payments after the scheduled date of the given id (or the same date, by higher id).
    * @param limit limit the quantity of results
    * @return list of 0 to $$limit payments
    */
  def scheduledAfter(id: Long, limit: Int = 100): Seq[Payment] = {
    sql"""
       $selectPayment
       FROM payments
       WHERE status='pending'
       AND (
         scheduled > (select scheduled from payments where id=$id)
         OR (scheduled = (select scheduled from payments where id=$id) AND id > $id)
       )
       ORDER BY scheduled ASC, id ASC
       LIMIT $limit;
    """.map(from).list().apply()
  }

  private def from(rs: WrappedResultSet): Payment =
    Payment(
      id = rs.longOpt("id"),
      source = AccountIdLike(rs.string("source")),
      destination = AccountIdLike(rs.string("destination")),
      code = rs.string("code"),
      issuer = rs.stringOpt("issuer").map(KeyPair.fromAccountId),
      units = rs.long("units"),
      received = ZonedDateTime.ofInstant(rs.timestamp("received").toInstant, UTC),
      scheduled = ZonedDateTime.ofInstant(rs.timestamp("scheduled").toInstant, UTC),
      submitted = rs.timestampOpt("submitted").map(_.toInstant).map(ZonedDateTime.ofInstant(_, UTC)),
      status = Payment.status(rs.string("status")),
      opResult = rs.stringOpt("op_result"),
      sourceResolved = rs.stringOpt("source_resolved").map(KeyPair.fromAccountId),
      destinationResolved = rs.stringOpt("destination_resolved").map(KeyPair.fromAccountId)
    )
}
