package models

import stellar.sdk.{KeyPair, PublicKeyOps}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

sealed trait AccountIdLike {
  def pk()(implicit ec: ExecutionContext): Future[PublicKeyOps]
  val account: String
}

case class RawAccountId(account: String) extends AccountIdLike {
  override def pk()(implicit ec: ExecutionContext): Future[PublicKeyOps] = Future(KeyPair.fromAccountId(account))
}

case class FederatedAddress(account: String) extends AccountIdLike {
  override def pk()(implicit ec: ExecutionContext): Future[PublicKeyOps] = KeyPair.fromAddress(account)
}

object AccountIdLike {

  private val fedAddressPattern = """^.+\*([a-z0-9]+(-[a-z0-9]+)*\.)+[a-z]{2,}$""".r.pattern

  def apply(s: String): AccountIdLike = {
    if (fedAddressPattern.matcher(s).matches()) FederatedAddress(s)
    else Try(KeyPair.fromAccountId(s)).toOption.map(_ => RawAccountId(s)).getOrElse {
      throw new IllegalArgumentException(s"Cannot resolve to account id: $s")
    }
  }
}