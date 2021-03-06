package models.db

import java.time.{ZoneId, ZonedDateTime}

import akka.Done
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import com.whisk.docker.impl.dockerjava.DockerKitDockerJava
import com.whisk.docker.specs2.DockerTestKit
import models.Generators._
import models.Payment
import org.scalacheck.Gen
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import play.api.db.Databases
import play.api.db.evolutions.{DatabaseEvolutions, ThisClassLoaderEvolutionsReader}
import scalikejdbc.ConnectionPool.DEFAULT_NAME
import scalikejdbc._
import scalikejdbc.specs2.mutable.AutoRollback

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class PaymentDaoSpec(ee: ExecutionEnv) extends Specification with LocalDaoSpec {

  sequential

  private def repo()(implicit session: DBSession) = new PaymentDao()

  class PaymentsState(ps: Seq[Payment]) extends AutoRollback {

    override def fixture(implicit session: DBSession): Unit = {
      val params = ps.map { p =>
        Seq(p.source.account, p.destination.account, p.code, p.issuer.map(_.accountId).orNull, p.units,
          p.received, p.scheduled, p.submitted.orNull, p.status.name, p.opResult.orNull,
          p.sourceResolved.map(_.accountId).orNull, p.destinationResolved.map(_.accountId).orNull)
      }
      sql"""
          insert into payments (source, destination, code, issuer, units, received, scheduled, submitted, status,
                                op_result, source_resolved, destination_resolved)
          values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.batch(params: _*).apply()
    }

    def fetchIds: Seq[Long] = sql"select id from payments".map(_.long(1)).list().apply
  }

  "count of history payments" should {
    val ps = sample(1000, genPayment)
    "equal the qty of payments with status succeeded or failed" in new PaymentsState(ps) {
      repo.countHistoric mustEqual
        ps.count(_.status == Payment.Succeeded) +
          ps.count(_.status == Payment.Failed) +
          ps.count(_.status == Payment.Invalid)
    }
  }

  "count of scheduled payments" should {
    val ps = sample(1000, genPayment)
    "equal the qty of payments with status of pending" in new PaymentsState(ps) {
      repo.countScheduled mustEqual ps.count(_.status == Payment.Pending)
    }
  }

  "list of due payments" should {
    "return nothing if there is nothing" in new PaymentsState(Nil) {
      repo.due must beEmpty
    }

    val psHistoric = sample(100, genHistoricPayment)
    "return nothing if there is nothing due" in new PaymentsState(psHistoric) {
      repo.due must beEmpty
    }

    val psScheduled = sample(80, genScheduledPayment)
    "return only those payments which are pending" in new PaymentsState(psHistoric ++ psScheduled) {
      repo.due.toList.map(_.copy(id = None, submitted = None)) must containTheSameElementsAs(
        psScheduled.filter(_.scheduled.isBefore(ZonedDateTime.now())).map(_.copy(id = None, status = Payment.Submitted))
      )
    }

    "work with autosession (inside play)" in {
      implicit val session: DBSession = AutoSession
      repo.due must beEmpty
    }
  }

  "submitting payments" should {
    val now = ZonedDateTime.now(UTC)
    val ps = sample(100, genScheduledPayment)

    "move the status to 'submitted' and update the submitted date" in new PaymentsState(ps) {
      val ids = sql"""select id from payments where status='pending'""".map(_.long(1)).list().apply()
      repo.submit(ids.take(50), now)

      repo.countScheduled mustEqual 50
      repo.countHistoric mustEqual 0

      val idsAndDates = sql"select id, submitted from payments where status='submitted'".map { rs =>
        rs.long("id") -> rs.timestampOpt("submitted").map(_.toInstant).map(ZonedDateTime.ofInstant(_, UTC))
      }.list().apply()

      idsAndDates.map(_._1) mustEqual ids.take(50)
      idsAndDates.map(_._2).distinct mustEqual Seq(Some(now))
    }
  }

  "confirming payments" should {
    val ps = sample(100, genSubmittedPayment)
    "move the status to 'successful' and set the op_result to 'OK'" in new PaymentsState(ps) {
      val ids = fetchIds
      repo.confirm(ids.take(50))
      val expected = ps.take(50).map(_.copy(id = None, opResult = Some("OK"), status = Payment.Succeeded))
      repo.history().map(_.copy(id = None)) must containTheSameElementsAs(expected)
    }
  }

  "rejecting payments" should {
    val ps = sample(100, genSubmittedPayment)
    "move the status to 'failed'" in new PaymentsState(ps) {
      val ids = fetchIds
      repo.reject(ids.take(50))
      val expected = ps.take(50).map(_.copy(id = None, status = Payment.Failed))
      repo.history().map(_.copy(id = None)) must containTheSameElementsAs(expected)
    }
  }

  "invalidating a payment" should {
    val ps = sample(3, genScheduledPayment)
    "move the status to 'failed'" in new PaymentsState(ps) {
      val ids = fetchIds
      val now = ZonedDateTime.now
      repo.invalidate(ids.head, now)
      val idsAndDates = sql"select id, submitted from payments where status='invalid'".map { rs =>
        rs.long("id") -> rs.timestampOpt("submitted").map(_.toInstant).map(ZonedDateTime.ofInstant(_, now.getZone))
      }.list().apply()

      idsAndDates mustEqual List(ids.head -> Some(now))
    }
  }

  "rejecting payments with an op result" should {
    val ps = sample(100, genSubmittedPayment)
    "move the status to 'failed' and update the op_result" in new PaymentsState(ps) {
      val ids = sql"""select id from payments""".map(_.long(1)).list().apply()
      repo.rejectWithOpResult(ids.take(50).zipWithIndex.map { case (l, r) => l -> r.toString })
      val expected = ps.take(50).zipWithIndex.map { case (p, id) =>
        p.copy(id = None, status = Payment.Failed, opResult = Some(id.toString))
      }
      repo.history().map(_.copy(id = None)) must containTheSameElementsAs(expected)
    }
  }

  "the earliest time due" should {
    val notDue = sample(100, genHistoricPayment)
    "return nothing if nothing is due" in new PaymentsState(notDue) {
      repo.earliestTimeDue must beNone
    }

    val scheduled = sample(100, genScheduledPayment)
    "return the earliest date due" in new PaymentsState(notDue ++ scheduled){
      val expectedDate = scheduled.map(_.scheduled).minBy(_.toInstant.toEpochMilli)
      repo.earliestTimeDue must beSome(expectedDate)
    }
  }

  "payment history" should {

    "return nothing if there is nothing" in new PaymentsState(Nil) {
      repo.history() must beEmpty
    }

    val scheduled = sample(100, genScheduledPayment)
    val submitted = sample(5, genSubmittedPayment)
    "return nothing if nothing has been submitted" in new PaymentsState(scheduled ++ submitted) {
      repo.history() must beEmpty
    }

    val historic = sample(75, genHistoricPayment)
    "return all historic payments in reverse order" in new PaymentsState(scheduled ++ submitted ++ historic) {
      val expected = historic.sortBy(_.submitted.get.toInstant.toEpochMilli).reverse.map(_.copy(id = None))
      repo.history().map(_.copy(id = None)) mustEqual expected
    }

    "limit the payments returned" in new PaymentsState(scheduled ++ submitted ++ historic) {
      val expected = historic.sortBy(_.submitted.get.toInstant.toEpochMilli).reverse.take(33).map(_.copy(id = None))
      repo.history(33).map(_.copy(id = None)) mustEqual expected
    }
  }

  "payment history before a given id" should {

    "return nothing if there is nothing" in new PaymentsState(Nil) {
      repo.historyBefore(100) must beEmpty
    }

    val historic = sample(75, genHistoricPayment)
    "return all historic payments in reverse order before the given id" in new PaymentsState(historic) {
      val historicWithRealIds = repo.history()
      val targetId = historicWithRealIds.drop(30).head.id.get
      val expected = historicWithRealIds.slice(31, 48)
      repo.historyBefore(targetId, 17) mustEqual expected
    }
  }

  "payment history after a given id" should {

    "return nothing if there is nothing" in new PaymentsState(Nil) {
      repo.historyAfter(100) must beEmpty
    }

    val historic = sample(75, genHistoricPayment)
    "return all historic payments in ascending order after the given id" in new PaymentsState(historic) {
      val historicWithRealIds = repo.history()
      val targetId = historicWithRealIds.drop(30).head.id.get
      val expected = historicWithRealIds.slice(13, 30).reverse
      repo.historyAfter(targetId, 17) mustEqual expected
    }
  }

  "payment schedule" should {

    "return nothing if there is nothing" in new PaymentsState(Nil) {
      repo.scheduled() must beEmpty
    }

    val history = sample(100, genHistoricPayment)
    val submitted = sample(5, genSubmittedPayment)
    "return nothing if nothing has been scheduled" in new PaymentsState(history ++ submitted) {
      repo.scheduled() must beEmpty
    }

    val scheduled = sample(75, genScheduledPayment)
    "return all scheduled payments in ascending order" in new PaymentsState(history ++ submitted ++ scheduled) {
      val expected = scheduled.sortBy(_.scheduled.toInstant.toEpochMilli).map(_.copy(id = None))
      repo.scheduled().map(_.copy(id = None)) mustEqual expected
    }

    "limit the payments returned" in new PaymentsState(history ++ submitted ++ scheduled) {
      val expected = scheduled.sortBy(_.scheduled.toInstant.toEpochMilli).take(33).map(_.copy(id = None))
      repo.scheduled(33).map(_.copy(id = None)) mustEqual expected
    }
  }

  "payment schedule before a given id" should {

    "return nothing if there is nothing" in new PaymentsState(Nil) {
      repo.scheduledBefore(100) must beEmpty
    }

    val scheduled = sample(15, genScheduledPayment)
    "return all scheduled payments in reverse order before the given id" in new PaymentsState(scheduled) {
      val scheduledWithRealId = repo.scheduled()
      val targetId = scheduledWithRealId.drop(10).head.id.get
      val expected = scheduledWithRealId.slice(3, 10).reverse
      repo.scheduledBefore(targetId, 7) mustEqual expected
    }
  }

  "payment schedule after a given id" should {
    "return nothing if there is nothing" in new PaymentsState(Nil) {
      repo.scheduledAfter(100) must beEmpty
    }

    val scheduled = sample(15, genScheduledPayment)
    "return all scheduled payments after the given id" in new PaymentsState(scheduled) {
      val scheduledWithRealId = repo.scheduled()
      val targetId = scheduledWithRealId.drop(5).head.id.get
      val expected = scheduledWithRealId.slice(6, 13)
      repo.scheduledAfter(targetId, 7) mustEqual expected
    }
  }

  "the payment sink" should {
    val ps = sample(280, genScheduledPayment)
    "insert all provided payments as new records" >> new PaymentsState(Nil) {
      val work: Future[Done] = Source.fromIterator(() => ps.iterator).runWith(repo.writer)
      Await.result(work, Duration.Inf)
      repo.scheduled(limit = 280).map(_.copy(id = None)) must containTheSameElementsAs(ps.map(_.copy(id = None)))
    }
  }
}
