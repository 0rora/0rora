package models

import java.time.{ZoneId, ZonedDateTime}

import org.scalacheck.Gen
import stellar.sdk.KeyPair
import stellar.sdk.model.Account

import scala.annotation.tailrec

object Generators {

  @tailrec
  def sampleOf[T](gen: Gen[T], tries: Int = 100): T = gen.sample match {
    case Some(t) => t
    case None =>
      require(tries > 0, "exhausted attempts to sample")
      sampleOf(gen, tries - 1)
  }

  def genScheduledPayment: Gen[Payment] = for {
    id <- Gen.posNum[Long]
    source = KeyPair.random.asPublicKey
    dest = KeyPair.random.asPublicKey
    units <- Gen.posNum[Long]
    date01 <- genDate
    date02 <- genDate
    Seq(received, scheduled) = Seq(date01, date02).sortBy(_.toInstant.toEpochMilli)
  } yield Payment(Some(id), source, dest, "XLM", None, units, received, scheduled, None, Payment.Pending, None)

  def genSubmittedPayment: Gen[Payment] = for {
    payment <- genScheduledPayment
    submitDate <- genDate
  } yield payment.copy(submitted = Some(submitDate), status = Payment.Submitted)

  def genSuccessfulPayment: Gen[Payment] =
    genSubmittedPayment.map(_.copy(status = Payment.Succeeded, opResult = Some("OK")))

  def genFailedPayment: Gen[Payment] =
    genSubmittedPayment.map(_.copy(status = Payment.Failed, opResult = Some("failure message")))

  def genHistoricPayment: Gen[Payment] = Gen.oneOf(genSuccessfulPayment, genFailedPayment)

  def genPayment: Gen[Payment] = Gen.oneOf(genHistoricPayment, genScheduledPayment, genSubmittedPayment)

  def genDate: Gen[ZonedDateTime] =
    Gen.chooseNum(Long.MinValue, Long.MaxValue)
      .map(delta => ZonedDateTime.now(ZoneId.of("UTC")).plusNanos(delta).withNano(0))

  def genAccount: Gen[Account] = Gen.posNum[Long].map(Account(KeyPair.random, _))

  def genURL: Gen[String] = for {
    schema <- Gen.oneOf("http", "https")
    host <- Gen.identifier
    port <- Gen.choose(80, 1024)
    segmentCount <- Gen.choose(0, 10)
    segments <- Gen.listOfN(segmentCount, Gen.identifier)
  } yield s"$schema://$host:$port/${segments.mkString("/")}"

}
