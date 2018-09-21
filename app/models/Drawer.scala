package models

import play.api.mvc.Call
import play.twirl.api.Html

case class Drawer(logout: Call) {
  def script: Html = Html("<script>drawer();</script>")
}
