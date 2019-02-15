package models

import java.time.{Instant, ZoneId, ZonedDateTime}

import models.repo.Payment
import org.scalacheck.Gen
import org.specs2.ScalaCheck
import stellar.sdk.KeyPair

object Generators {

//  def genHistoricPayment: Gen[Payment] = Gen.oneOf(genSuccessfulPayment, genFailedPayment)

  def genSuccessfulPayment: Gen[Payment] = for {
    id <- Gen.posNum[Long]
    source = KeyPair.random.asPublicKey
    dest = KeyPair.random.asPublicKey
    units <- Gen.posNum[Long]
    date01 <- genDate
    date02 <- genDate
    date03 <- genDate
    Seq(received, scheduled, submitted) = Seq(date01, date02, date03).sortBy(_.toInstant.toEpochMilli)
  } yield Payment(Some(id), source, dest, "XLM", None, units, received, scheduled, Some(submitted), Payment.Succeeded, Some("succeeded"))

  def genDate: Gen[ZonedDateTime] = Gen.posNum[Long].map(Instant.ofEpochMilli).map(ZonedDateTime.ofInstant(_, ZoneId.of("UTC")))

}
