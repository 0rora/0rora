package models

import java.net.URI

import javax.inject.{Inject, Singleton}
import play.api.Configuration
import stellar.sdk.{KeyPair, Network, PublicNetwork, StandaloneNetwork, TestNetwork}

import scala.util.{Failure, Try}

@Singleton
class AppConfig @Inject()(val underlying: Configuration) {

  val network: Network = underlying.get[String]("0rora.horizon") match {
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

  val signerKey: KeyPair = KeyPair.fromSecretSeed(underlying.get[String]("0rora.account.secret"))

}

case class InvalidConfig(msg: String, t: Throwable) extends RuntimeException(msg, t)