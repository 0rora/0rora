package models

import play.api.mvc.Call
import play.twirl.api.Html

case class TopBar(logout: Call) {
  def script: Html = Html("<script>topAppBar();</script>")
}
