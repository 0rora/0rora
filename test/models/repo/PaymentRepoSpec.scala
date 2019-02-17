package models.repo

import java.time.ZonedDateTime

import models.Generators
import models.Generators.genPayment
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
//    addFixtures()
    database = Some(db)
  }

  def afterAll(): Unit = {
    database.foreach(Evolutions.cleanupEvolutions(_))
    database.foreach(_.shutdown())
  }

  private val repo = new PaymentRepo

  "count of history payments" should {
    "equal the qty of payments with status succeeded or failed" in new AutoRollback {
      var historicCount: Int = 0

      override def fixture(implicit session: DBSession): Unit = {
        val payments = insert(1000, genPayment)
        historicCount = payments.count(p => Set[Payment.Status](Payment.Succeeded, Payment.Failed).contains(p.status))
      }

      repo.countHistoric mustEqual historicCount
    }
  }

  "count of scheduled payments" should {
    "equal the qty of payments with status of pending" in new AutoRollback {
      var scheduledCount: Int = 0

      override def fixture(implicit session: DBSession): Unit = {
        val payments = insert(1000, genPayment)
        scheduledCount = payments.count(_.status == Payment.Pending)
      }

      repo.countScheduled mustEqual scheduledCount
    }
  }

  private def insert(qty: Int, gen: Gen[Payment])(implicit s: DBSession): Seq[Payment] = {
    val payments = Array.fill(1000)(genPayment.sample).flatten
    val params = payments.map { p =>
      Seq(p.source.accountId, p.destination.accountId, p.code, p.issuer.map(_.accountId).orNull, p.units,
        p.received, p.scheduled, p.submitted.orNull, p.status.name, p.opResult.orNull)
    }
    sql"""
          insert into payments (source, destination, code, issuer, units, received, scheduled, submitted, status, op_result)
          values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.batch(params: _*).apply()
    payments
  }
}
