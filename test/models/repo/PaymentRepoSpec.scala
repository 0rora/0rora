package models.repo

import java.time.{ZoneId, ZonedDateTime}

import models.Generators._
import models.Payment
import org.scalacheck.Gen
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAfterAll
import play.api.db.evolutions.{DatabaseEvolutions, Evolutions, ThisClassLoaderEvolutionsReader}
import play.api.db.{Database, Databases}
import scalikejdbc.ConnectionPool.DEFAULT_NAME
import scalikejdbc._
import scalikejdbc.specs2.mutable.AutoRollback
import stellar.sdk.KeyPair

class PaymentRepoSpec extends Specification with BeforeAfterAll {

  sequential

  private val UTC = ZoneId.of("UTC")
  private var database: Option[Database] = None
  private val (source, dest) = (KeyPair.random, KeyPair.random)
  private val (fiveDaysAgo, now, fiveDaysFromNow) = {
    val now = ZonedDateTime.now()
    (now.minusDays(5).toInstant, now.toInstant, now.plusDays(5).toInstant)
  }

  def beforeAll(): Unit = {
    val db = Databases.inMemory(
      name = "default",
      urlOptions = Map("MODE" -> "PostgreSQL", "DATABASE_TO_UPPER" -> "FALSE"),
      config = Map()
    )
    val evolutions = new DatabaseEvolutions(db, "")
    val scripts = evolutions.scripts(ThisClassLoaderEvolutionsReader)
    evolutions.evolve(scripts, autocommit = true)
    ConnectionPool.add(DEFAULT_NAME, new DataSourceConnectionPool(db.dataSource))
    database = Some(db)
  }

  def afterAll(): Unit = {
    database.foreach(Evolutions.cleanupEvolutions(_))
    database.foreach(_.shutdown())
  }

  private def repo()(implicit session: DBSession) = new PaymentRepo()

  class PaymentsState(ps: Seq[Payment]) extends AutoRollback {
    override def fixture(implicit session: DBSession): Unit = {
      val params = ps.map { p =>
        Seq(p.source.accountId, p.destination.accountId, p.code, p.issuer.map(_.accountId).orNull, p.units,
          p.received, p.scheduled, p.submitted.orNull, p.status.name, p.opResult.orNull)
      }
      sql"""
          insert into payments (source, destination, code, issuer, units, received, scheduled, submitted, status, op_result)
          values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.batch(params: _*).apply()
    }
  }

  "count of history payments" should {
    val ps = sample(1000, genPayment)
    "equal the qty of payments with status succeeded or failed" in new PaymentsState(ps) {
      repo.countHistoric mustEqual ps.count(_.status == Payment.Succeeded) + ps.count(_.status == Payment.Failed)
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
      repo.due(100) must beEmpty
    }

    val psHistoric = sample(100, genHistoricPayment)
    "return nothing if there is nothing due" in new PaymentsState(psHistoric) {
      repo.due(150) must beEmpty
    }

    val psScheduled = sample(80, genScheduledPayment)
    "return only those payments which are pending" in new PaymentsState(psHistoric ++ psScheduled) {
      repo.due(100).map(_.copy(id = None)) must containTheSameElementsAs(
        psScheduled.filter(_.scheduled.isBefore(ZonedDateTime.now())).map(_.copy(id = None))
      )
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

  private def sample(qty: Int, gen: Gen[Payment]): Seq[Payment] = Array.fill(qty)(gen.sample).toSeq.flatten
}
