package models.db

import java.time.ZoneId

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.whisk.docker.impl.dockerjava.DockerKitDockerJava
import com.whisk.docker.specs2.DockerTestKit
import models.Payment
import org.scalacheck.Gen
import play.api.db.Databases
import play.api.db.evolutions.{DatabaseEvolutions, ThisClassLoaderEvolutionsReader}
import scalikejdbc.ConnectionPool.DEFAULT_NAME
import scalikejdbc.{ConnectionPool, DataSourceConnectionPool}

trait LocalDaoSpec extends DockerTestKit with DockerKitDockerJava with DockerPostgresService {

  val UTC: ZoneId = ZoneId.of("UTC")
  implicit val sys: ActorSystem = ActorSystem("AccountDaoSpec")
  implicit val mat: ActorMaterializer = ActorMaterializer()

  override def beforeAll(): Unit = {
    try {
      startAllOrFail()
    } catch {
      case t: Throwable => t.printStackTrace()
    }

    val db = Databases(
      driver = "org.postgresql.Driver",
      url = s"jdbc:postgresql://127.0.0.1:$PostgresExposedPort/",
      name = "default",
      config = Map(
        "username" -> PostgresUser,
        "password" -> PostgresPassword,
        "logStatements" -> true
      )
    )
    val evolutions = new DatabaseEvolutions(db, "")
    val scripts = evolutions.scripts(ThisClassLoaderEvolutionsReader)

    evolutions.evolve(scripts, autocommit = true)
    ConnectionPool.add(DEFAULT_NAME, new DataSourceConnectionPool(db.dataSource))
  }

  def sample[T](qty: Int, gen: Gen[T]): Seq[T] = Array.fill(qty)(gen.sample).toSeq.flatten

}
