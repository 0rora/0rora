package controllers

import java.sql.SQLException

import javax.inject.Inject
import models.AppConfig
import models.repo.AccountRepo
import org.pac4j.core.profile.CommonProfile
import org.pac4j.play.scala.{Security, SecurityComponents}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, BaseController}
import stellar.sdk.KeyPair
import stellar.sdk.model.NativeAsset

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class AdminController @Inject()(val controllerComponents: SecurityComponents,
                                accountRepo: AccountRepo,
                                config: AppConfig) extends BaseController with Security[CommonProfile] {

  implicit private val ec: ExecutionContext = controllerComponents.executionContext
  private val DuplicateKeyViolationCode = "23505"

  def addAccount(): Action[AnyContent] = Secure("FormClient").async { implicit req =>
    req.body.asFormUrlEncoded.flatMap(_.get("seed").flatMap(_.headOption)) match {
      case Some(seed) =>
        Try(KeyPair.fromSecretSeed(seed)) match {
          case Success(kp) =>
            config.network.account(kp).map { accn =>
              Try(accountRepo.insert(kp)) match {
                case Success(_) => Ok(Json.toJson(Map("account" -> kp.accountId)))
                case Failure(t: SQLException) if t.getSQLState == DuplicateKeyViolationCode =>
                  BadRequest(Json.toJson(Map("failure" -> s"The account already exists.")))
                case Failure(t) => InternalServerError(t.getMessage)
              }
            }.recover { case _ =>
              BadRequest(Json.toJson(Map("failure" -> s"The account does not exist.")))
            }
          case Failure(_) => Future.successful(BadRequest(Json.toJson(Map("failure" -> "The seed is invalid."))))
        }
      case _ => Future.successful(BadRequest(Json.toJson(Map("failure" -> "No seed was sent."))))
    }
  }
}
