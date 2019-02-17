package models

import java.time.{Instant, ZoneId, ZonedDateTime}

import models.Generators.genDate
import models.repo.Payment
import org.scalacheck.Gen
import org.specs2.ScalaCheck
import stellar.sdk.KeyPair

object Generators {

  def genScheduledPayment: Gen[Payment] = for {
    id <- Gen.posNum[Long]
    source = KeyPair.random.asPublicKey
    dest = KeyPair.random.asPublicKey
    units <- Gen.posNum[Long]
    date01 <- genDate
    date02 <- genDate
    Seq(received, scheduled) = Seq(date01, date02).sortBy(_.toInstant.toEpochMilli)
  } yield Payment(Some(id), source, dest, "XLM", None, units, received, scheduled, None, Payment.Pending, None)

  def genSuccessfulPayment: Gen[Payment] = for {
    payment <- genScheduledPayment
    submitDate <- genDate
  } yield payment.copy(submitted = Some(submitDate), status = Payment.Succeeded)

  def genFailedPayment: Gen[Payment] = for {
    successful <- genSuccessfulPayment
    submitDate <- genDate
  } yield successful.copy(submitted = Some(submitDate), status = Payment.Failed, opResult = Some("failure message"))

  def genHistoricPayment: Gen[Payment] = Gen.oneOf(genSuccessfulPayment, genFailedPayment)

  def genPayment: Gen[Payment] = Gen.oneOf(genHistoricPayment, genScheduledPayment)

  def genDate: Gen[ZonedDateTime] =
    Gen.chooseNum(Long.MinValue, Long.MaxValue)
      .map(delta => ZonedDateTime.now(ZoneId.of("UTC")).plusNanos(delta).withNano(0))

}
