package models

import java.net.URI

import javax.inject.{Inject, Singleton}
import play.api.Configuration
import stellar.sdk.{KeyPair, Network, PublicNetwork, StandaloneNetwork, TestNetwork}

import scala.collection.JavaConverters._
import scala.util.{Failure, Try}

trait AppConfig {
  val network: Network
  val accounts: Map[String, KeyPair]
}

@Singleton
class FileAppConfig @Inject()(val conf: Configuration) extends AppConfig {

  val network: Network = conf.get[String]("0rora.horizon") match {
    case "test" => TestNetwork
    case "public" => PublicNetwork
    case s =>
      Try(new URI(s).toURL).map(_.toURI).map(StandaloneNetwork).recover { case t =>
        throw InvalidConfig(s"Configured network is unknown: `0rora.horizon = $s`", t)
      }.get
  }

  val accounts: Map[String, KeyPair] =
    conf.underlying.getStringList("0rora.accounts").asScala.map(KeyPair.fromSecretSeed)
      .map(kp => kp.accountId -> kp).toMap

}

case class InvalidConfig(msg: String, t: Throwable) extends RuntimeException(msg, t)