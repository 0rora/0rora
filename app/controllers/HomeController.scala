package controllers

import javax.inject._
import org.pac4j.core.profile.CommonProfile
import org.pac4j.http.client.indirect.FormClient
import org.pac4j.play.scala.{Security, SecurityComponents}
import play.api.http.HttpVerbs._
import play.api.mvc._

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(val controllerComponents: SecurityComponents) extends Security[CommonProfile] {

  def login(): Action[AnyContent] = Action { implicit req =>
    val formClient = config.getClients.findClient("FormClient").asInstanceOf[FormClient]
    val call = Call(GET, formClient.getCallbackUrl)
    Ok(views.html.login(call))
  }
}

