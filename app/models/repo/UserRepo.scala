package models.repo

import javax.inject.Inject
import models.User

@javax.inject.Singleton
class UserRepo @Inject()() {

  private val validUsers = Set(User("demo", "demo"), User("foo", "bar"))

  val lookupUser: User => Boolean = validUsers.contains

}
