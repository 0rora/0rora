package models.repo

import javax.inject.Inject
import org.pac4j.play.store.DataEncrypter
import scalikejdbc.{DBSession, _}
import stellar.sdk.KeyPair

@javax.inject.Singleton
class AccountRepo @Inject()()(implicit val session: DBSession, encrypter: DataEncrypter) {

  def insert(kp: KeyPair): Unit = {
    sql"insert into accounts (id, seed) values (${kp.accountId}, ${encrypter.encrypt(kp.secretSeed.map(_.toByte))})".
      update().apply()
  }

}
