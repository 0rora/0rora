package controllers

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME

import akka.util.ByteString
import javax.inject.{Inject, Singleton}
import models.AppConfig
import org.pac4j.core.profile.CommonProfile
import org.pac4j.play.scala.{Security, SecurityComponents}
import play.api.http.HttpEntity.Strict
import play.api.mvc._
import stellar.sdk.KeyPair

import scala.util.Random

@Singleton
class DemoSiteAssetsController @Inject()(val controllerComponents: SecurityComponents,
                                         config: AppConfig) extends BaseController with Security[CommonProfile] {

  private def format(zdt: ZonedDateTime): String = ISO_OFFSET_DATE_TIME.format(zdt)
  private val accounts: Seq[KeyPair] = config.accounts.values.toSeq

  def paymentsCSV: Action[AnyContent] =Secure("FormClient") { implicit req =>
    val now = ZonedDateTime.now()
    Ok.sendEntity(Strict(
      ByteString(
        s"""sender,recipient,asset,issuer,amount,schedule
           |$csvAccounts,XLM,,5000,${format(now.minusMinutes(30))}
           |$csvAccounts,XLM,,5000,${format(now)}
           |$csvAccounts,XLM,,5000,${format(now.plusMinutes(1))}
           |GCCGEJC45TVQUVQ3HH6JUFD4Q3NO3XYTMWWKUVJP6Z3MR7D3YLOB6XXC,GBFEQSR3ZDWANTL4BFKVUS4A73HBFRSBLSBJE5POKICVT2OGDK2AVLCY,BananaMan,GCCGEJC45TVQUVQ3HH6JUFD4Q3NO3XYTMWWKUVJP6Z3MR7D3YLOB6XXC,5000,${format(now)}
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
