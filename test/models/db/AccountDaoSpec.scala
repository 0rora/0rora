package models.db

import models.Generators.genKeyPair
import org.pac4j.play.store.{DataEncrypter, ShiroAesDataEncrypter}
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import scalikejdbc.specs2.mutable.AutoRollback

class AccountDaoSpec(ee: ExecutionEnv) extends Specification with LocalDaoSpec {

  sequential

  implicit val dataEncrypter: DataEncrypter = new ShiroAesDataEncrypter("passphrase123456")

  class AccountsState() extends AutoRollback

  "inserting an account" should {
    "mean that the account is returned in the list" in new AccountsState() {
      val kps = sample(qty = 100, genKeyPair)
      val repo = new AccountDao()
      kps.foreach(repo.insert)
      repo.list.map(_.secretSeed.mkString) must containTheSameElementsAs(kps.map(_.secretSeed.mkString))
    }
  }

}
