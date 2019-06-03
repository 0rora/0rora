package models.repo

import javax.inject.Inject
import org.pac4j.play.store.DataEncrypter
import scalikejdbc.{DBSession, _}
import stellar.sdk.{KeyPair, PublicKey}

// TODO (jem) - *Repo classes to be renamed to avoid confusion with the repo actors.

@javax.inject.Singleton
class AccountRepo @Inject()()(implicit val session: DBSession, encrypter: DataEncrypter) {

  def insert(kp: KeyPair): Unit = {
    sql"insert into accounts (id, seed) values (${kp.accountId}, ${encrypter.encrypt(kp.secretSeed.map(_.toByte))})".
      update().apply()
  }

  def list: Seq[KeyPair] = {
    sql"select seed from accounts order by id".map { rs =>
      val secret = encrypter.decrypt(rs.bytes("seed")).map(_.toChar)
      KeyPair.fromSecretSeed(secret)
    }.list().apply()
  }

}
