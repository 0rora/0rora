package models

import akka.NotUsed
import akka.stream.scaladsl.Source
import org.json4s.CustomSerializer
import stellar.sdk.inet.HorizonAccess
import stellar.sdk.model.response.{AccountResponse, TransactionApproved, TransactionPostResponse}
import stellar.sdk.model.{HorizonCursor, HorizonOrder, SignedTransaction}
import stellar.sdk.{Network, PublicKeyOps}

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

case class StubNetwork(respondWith: Seq[TransactionPostResponse] = Seq(TransactionApproved("", 1, "", "", "")),
                       fail: Boolean = false) extends Network {
  override def passphrase: String = "stub network"

  val posted: mutable.Buffer[SignedTransaction] = mutable.Buffer.empty
  var response: Int = 0

  override val horizon: HorizonAccess = new HorizonAccess {
    override def post(txn: SignedTransaction)(implicit ec: ExecutionContext): Future[TransactionPostResponse] = {
      posted.append(txn)
      if (fail) Future.failed(new RuntimeException("failed for test purposes"))
      else {
        val transactionPostResponse = respondWith(response)
        response = math.min(response + 1, respondWith.size - 1)
        Future.successful(transactionPostResponse)
      }
    }
    override def get[T](path: String, params: Map[String, String])(implicit evidence$1: ClassTag[T], ec: ExecutionContext, m: Manifest[T]): Future[T] = ???
    override def getStream[T](path: String, de: CustomSerializer[T], cursor: HorizonCursor, order: HorizonOrder, params: Map[String, String])(implicit evidence$2: ClassTag[T], ec: ExecutionContext, m: Manifest[T]): Future[Stream[T]] = ???
    override def getSource[T](path: String, de: CustomSerializer[T], cursor: HorizonCursor, params: Map[String, String])(implicit evidence$3: ClassTag[T], ec: ExecutionContext, m: Manifest[T]): Source[T, NotUsed] = ???
  }

  private var expectedAccounts: Map[PublicKeyOps, AccountResponse] = Map.empty

  def expectAccount(pk: PublicKeyOps, accountResponse: AccountResponse): StubNetwork = {
    expectedAccounts = expectedAccounts.updated(pk, accountResponse)
    this
  }

  override def account(pk: PublicKeyOps)(implicit ec: ExecutionContext): Future[AccountResponse] =
    Future(expectedAccounts(pk))
}
