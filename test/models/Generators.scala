package models

import java.time.{ZoneId, ZonedDateTime}

import org.scalacheck.Gen
import stellar.sdk.KeyPair
import stellar.sdk.model.Account
import stellar.sdk.model.result._

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
    source = AccountIdLike(KeyPair.random.accountId)
    dest = AccountIdLike(KeyPair.random.accountId)
    units <- Gen.posNum[Long]
    date01 <- genDate
    date02 <- genDate
    Seq(received, scheduled) = Seq(date01, date02).sortBy(_.toInstant.toEpochMilli)
  } yield Payment(Some(id), source, dest, "XLM", None, units, received, scheduled, None, Payment.Pending, None)

  def genValidatedPayment: Gen[Payment] = genScheduledPayment.map(p => p.copy(
    sourceResolved = Some(KeyPair.fromAccountId(p.source.account)),
    destinationResolved = Some(KeyPair.fromAccountId(p.destination.account)),
    status = Payment.Valid
  ))

  def genSubmittedPayment: Gen[Payment] = for {
    payment <- genValidatedPayment
    submitDate <- genDate
  } yield payment.copy(submitted = Some(submitDate), status = Payment.Submitted)

  def genSuccessfulPayment: Gen[Payment] =
    genSubmittedPayment.map(_.copy(status = Payment.Succeeded, opResult = Some("OK")))

  def genFailedPayment: Gen[Payment] =
    genSubmittedPayment.map(_.copy(status = Payment.Failed, opResult = Some("failure message")))

  def genHistoricPayment: Gen[Payment] = Gen.oneOf(genSuccessfulPayment, genFailedPayment)

  def genInvalidPayment: Gen[Payment] = genHistoricPayment.map(_.copy(status = Payment.Invalid))

  def genPayment: Gen[Payment] = Gen.oneOf(genHistoricPayment, genScheduledPayment, genSubmittedPayment, genInvalidPayment)

  def genDate: Gen[ZonedDateTime] =
    Gen.chooseNum(Long.MinValue, Long.MaxValue)
      .map(delta => ZonedDateTime.now(ZoneId.of("UTC")).plusNanos(delta).withNano(0))

  def genAccount: Gen[Account] = for {
    seqNo <- Gen.posNum[Long]
    kp <- genKeyPair
  } yield Account(kp, seqNo)

  def genKeyPair: Gen[KeyPair] = Gen.alphaNumChar.map(_ => KeyPair.random)

  def genURL: Gen[String] = for {
    schema <- Gen.oneOf("http", "https")
    host <- genDomainName
    port <- Gen.choose(80, 1024)
    segmentCount <- Gen.choose(0, 10)
    segments <- Gen.listOfN(segmentCount, Gen.identifier)
  } yield s"$schema://$host:$port/${segments.mkString("/")}"

  def genDomainName: Gen[String] = for {
    tld <- Gen.choose(2, 10).flatMap(Gen.listOfN(_, Gen.alphaChar).map(_.mkString))
    labels <- Gen.choose(1, 8).flatMap(Gen.listOfN(_, Gen.alphaNumStr.suchThat(_.nonEmpty).map(_.take(10))))
    dn = s"${labels.mkString(".")}.$tld".toLowerCase()
  } yield if (dn.length > 253) dn.takeRight(253) else dn

  def genFederatedName: Gen[String] = for {
    name <- Gen.alphaNumStr.suchThat(_.nonEmpty)
    domain <- genDomainName
  } yield s"$name*$domain"

  def genPaymentOpResultFailure: Gen[PaymentResult] = Gen.oneOf(
    PaymentMalformed,
    PaymentUnderfunded,
    PaymentSourceNoTrust,
    PaymentSourceNotAuthorised,
    PaymentNoDestination,
    PaymentDestinationNoTrust,
    PaymentDestinationNotAuthorised,
    PaymentDestinationLineFull,
    PaymentNoIssuer)
}
