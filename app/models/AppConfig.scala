package models

import java.net.URI

import javax.inject.{Inject, Singleton}
import play.api.Configuration
import stellar.sdk.{KeyPair, Network, PublicNetwork, StandaloneNetwork, TestNetwork}

import scala.collection.JavaConverters._
import scala.util.{Failure, Try}

@Singleton
class AppConfig @Inject()(val conf: Configuration) {

  val network: Network = conf.get[String]("0rora.horizon") match {
    case "test" => TestNetwork
    case "public" => PublicNetwork
    case s =>
      val uri = new URI(s)
      Try(uri.toURL) match {
        case Failure(t) => throw InvalidConfig(s"Configured network is unknown: `0rora.horizon = $s`", t)
        case _ =>
      }
      StandaloneNetwork(uri)
  }

  val accounts: Map[String, KeyPair] =
    conf.underlying.getStringList("0rora.accounts").asScala.map(KeyPair.fromSecretSeed)
      .map(kp => kp.accountId -> kp).toMap

}

case class InvalidConfig(msg: String, t: Throwable) extends RuntimeException(msg, t)