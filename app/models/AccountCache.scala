package models

import play.api.Logger
import stellar.sdk.model.Account

class AccountCache() {

  private var available: Map[String, Account] = Map.empty[String, Account]
  private var busy: Map[String, Account] = Map.empty[String, Account]

  def returnAccount(a: Account): Unit = {
    val key = a.publicKey.accountId
    Logger.debug(s"[$key] is now available.")
    available = available.updated(key, a)
    busy = busy.filterKeys(_ != key)
  }

  def borrowAccount: Option[Account] = available.toSeq match {
    case (key, accn) +: tail =>
      available = tail.toMap
      busy = busy.updated(key, accn)
      Logger.debug(s"[$key] is now busy.")
      Some(accn)
    case _ => None
  }

  def retire(a: Account): Unit = {
    val key = a.publicKey.accountId
    Logger.debug(s"[$key] is retired.")
    available = available.filterKeys(_ != key)
    busy = busy.filterKeys(_ != key)
    if (available.isEmpty && busy.isEmpty) {
      Logger.warn(s"There are no accounts left in the cache. All have been retired.")
    }
  }

  def readyCount: Int = available.size
}
