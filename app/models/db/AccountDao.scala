package models.db

import java.sql.SQLException

import javax.inject.Inject
import org.pac4j.play.store.DataEncrypter
import scalikejdbc.{DBSession, _}
import stellar.sdk.{KeyPair, PublicKey}

@javax.inject.Singleton
class AccountDao @Inject()()(implicit val session: DBSession, encrypter: DataEncrypter) {

  @throws(classOf[SQLException])
  def insert(kp: KeyPair): Unit = {
    sql"insert into accounts (id, seed) values (${kp.accountId}, ${encrypter.encrypt(kp.secretSeed.map(_.toByte))})".
      update().apply()
  }

  def list: Seq[KeyPair] = {
    sql"select seed from accounts order by id".map { rs =>
      val secret = encrypter.decrypt(rs.bytes("seed")).map(_.toChar)
      KeyPair.fromSecretSeed(secret.mkString)
    }.list().apply()
  }

}
