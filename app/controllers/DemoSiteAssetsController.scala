package controllers

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME

import akka.util.ByteString
import controllers.actions.AuthenticatedUserAction
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.http.HttpEntity.Strict
import play.api.mvc._
import stellar.sdk.KeyPair

@Singleton
class DemoSiteAssetsController @Inject()(cc: ControllerComponents,
                                         config: Configuration,
                                         authenticatedUserAction: AuthenticatedUserAction) extends AbstractController(cc) {

  private val sender = KeyPair.fromSecretSeed(config.get[String]("0rora.account.secret")).accountId
  private val recipient = "GACZHAQLFECAHDSFDQPCOAD6ITVWR7BUZAIRRUGOAPLECX74O6222A4G"
  private def format(zdt: ZonedDateTime): String = ISO_OFFSET_DATE_TIME.format(zdt)

  def paymentsCSV: Action[AnyContent] = authenticatedUserAction { implicit request: Request[AnyContent] =>
    val now = ZonedDateTime.now()
    Ok.sendEntity(Strict(
      ByteString(
        s"""sender,recipient,asset,issuer,amount,schedule
           |$sender,$recipient,XLM,,5000,${format(now.minusMinutes(30))}
           |$sender,$recipient,XLM,,5000,${format(now)}
           |$sender,$recipient,XLM,,5000,${format(now.plusMinutes(5))}
        """.stripMargin), Some("application/x-download")
    ))
  }
}
