package controllers.actions

import play.api.mvc.Call

// todo - topbar should be an optional object that includes the logoutUrl
case class PageSections(loginForm: Boolean = false,
                        topBar: Boolean = false,
                        logoutUrl: Option[Call] = None)
