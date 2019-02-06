package controllers

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME

import akka.util.ByteString
import controllers.actions.AuthenticatedUserAction
import javax.inject.{Inject, Singleton}
import models.AppConfig
import play.api.http.HttpEntity.Strict
import play.api.mvc._
import stellar.sdk.KeyPair

import scala.util.Random

@Singleton
class DemoSiteAssetsController @Inject()(cc: ControllerComponents,
                                         config: AppConfig,
                                         authenticatedUserAction: AuthenticatedUserAction) extends AbstractController(cc) {

  private def format(zdt: ZonedDateTime): String = ISO_OFFSET_DATE_TIME.format(zdt)
  private val accounts: Seq[KeyPair] = config.accounts.values.toSeq

  def paymentsCSV: Action[AnyContent] = authenticatedUserAction { implicit request: Request[AnyContent] =>
    val now = ZonedDateTime.now()
    Ok.sendEntity(Strict(
      ByteString(
        s"""sender,recipient,asset,issuer,amount,schedule
           |$csvAccounts,XLM,,5000,${format(now.minusMinutes(30))}
           |$csvAccounts,XLM,,5000,${format(now)}
           |$csvAccounts,XLM,,5000,${format(now.plusMinutes(1))}
        """.stripMargin), Some("application/x-download")
    ))
  }

  private def csvAccounts: String = {
    val (s, d) = chooseFrom(accounts)
    s"${s.accountId},${d.accountId}"
  }

  private def chooseFrom(accounts: Seq[KeyPair]): (KeyPair, KeyPair) = {
    val s = accounts(Random.nextInt(accounts.size))
    val d = accounts.filterNot(_.accountId == s.accountId)(Random.nextInt(accounts.size - 1))
    s -> d
  }

}
