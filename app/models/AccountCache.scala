package models

import play.api.Logger
import stellar.sdk.model.Account

class AccountCache() {

  private var available: Map[String, Account] = Map.empty[String, Account]
  private var busy: Map[String, Account] = Map.empty[String, Account]
  private val logger = Logger("account-cache")

  def returnAccount(a: Account): Unit = {
    val key = a.publicKey.accountId
    logger.debug(s"[$key] is now available.")
    available = available.updated(key, a)
    busy = busy.filterKeys(_ != key)
  }

  def borrowAccount: Option[Account] = available.toSeq match {
    case (key, accn) +: tail =>
      available = tail.toMap
      busy = busy.updated(key, accn)
      logger.debug(s"[$key] is now busy.")
      Some(accn)
    case _ => None
  }

  def retire(a: Account): Unit = {
    val key = a.publicKey.accountId
    logger.debug(s"[$key] is retired.")
    available = available.filterKeys(_ != key)
    busy = busy.filterKeys(_ != key)
    if (available.isEmpty && busy.isEmpty) {
      logger.warn(s"There are no accounts left in the cache. All have been retired.")
    }
  }

  def readyCount: Int = available.size
}
