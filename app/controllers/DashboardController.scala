package controllers

import javax.inject.Inject
import org.pac4j.core.profile.CommonProfile
import org.pac4j.play.scala.{Security, SecurityComponents}
import play.api.mvc._

class DashboardController @Inject()(val controllerComponents: SecurityComponents)
  extends BaseController with Security[CommonProfile] {

  def dashboard(): Action[AnyContent] = Secure("FormClient") { implicit req =>
    val profile: CommonProfile = profiles(req).head
    Ok(views.html.main(profile.getUsername))
  }
}