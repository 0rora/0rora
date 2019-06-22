package models.db

import java.sql.DriverManager

import com.whisk.docker.{DockerCommandExecutor, DockerContainer, DockerContainerState, DockerKit, DockerReadyChecker}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Random, Try}
import scala.concurrent.duration._

trait DockerPostgresService extends DockerKit {
  def PostgresAdvertisedPort = 5432
  def PostgresExposedPort = 44444
  val PostgresUser = "dacshund"
  val PostgresPassword: String = Random.alphanumeric.take(10).mkString

  val postgresContainer = DockerContainer("postgres:9.5.3")
    .withPorts((PostgresAdvertisedPort, Some(PostgresExposedPort)))
    .withEnv(s"POSTGRES_USER=$PostgresUser", s"POSTGRES_PASSWORD=$PostgresPassword")
    .withReadyChecker(
      new PostgresReadyChecker(PostgresUser, PostgresPassword, Some(PostgresExposedPort))
        .looped(15, 1.second)
    )

  abstract override def dockerContainers: List[DockerContainer] =
    postgresContainer :: super.dockerContainers
}

class PostgresReadyChecker(user: String, password: String, port: Option[Int] = None)
  extends DockerReadyChecker {

  override def apply(container: DockerContainerState)(implicit docker: DockerCommandExecutor,
                                                      ec: ExecutionContext): Future[Boolean] =
    container
      .getPorts()
      .map(ports =>
        Try {
          Class.forName("org.postgresql.Driver")
          val url = s"jdbc:postgresql://${docker.host}:${port.getOrElse(ports.values.head)}/"
          Option(DriverManager.getConnection(url, user, password)).map(_.close).isDefined
        }.getOrElse(false))
}
