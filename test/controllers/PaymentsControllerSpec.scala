package controllers

import java.time.{Instant, ZoneId, ZonedDateTime}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import models.Generators.genSuccessfulPayment
import models.db.PaymentDao
import models.{AccountIdLike, Payment}
import org.scalacheck.{Arbitrary, Gen}
import org.specs2.ScalaCheck
import org.specs2.concurrent.ExecutionEnv
import org.specs2.execute.Results
import org.specs2.mock.Mockito
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json.{JsPath, Json, Reads, _}
import play.api.test._
import stellar.sdk.{KeyPair, PublicKeyOps}


class PaymentsControllerSpec(implicit ec: ExecutionEnv) extends PlaySpecification with Results with Mockito with ScalaCheck {
  implicit val sys: ActorSystem = ActorSystem("PaymentsControllerSpec")
  implicit val mat: ActorMaterializer = ActorMaterializer()

  private def epochMillisToUTCDateTime(m: Long) =
    ZonedDateTime.ofInstant(Instant.ofEpochMilli(m), ZoneId.of("UTC"))

  implicit val paymentReads: Reads[Payment] = (
    (JsPath \ "id").readNullable[Long] and
      (JsPath \ "scheduled").read[Long].map(epochMillisToUTCDateTime) and
      (JsPath \ "submitted").readNullable[Long].map(_.map(epochMillisToUTCDateTime)) and
      (JsPath \ "from").read[String].map(AccountIdLike.apply) and
      (JsPath \ "to").read[String].map(AccountIdLike.apply) and
      (JsPath \ "asset").read[String] and
      (JsPath \ "units").read[Double].map(BigDecimal.apply).map(_ * 10000000.0).map(_.doubleValue) and
      (JsPath \ "status").read[String].map(Payment.status) and
      (JsPath \ "result").readNullable[String] and
      (JsPath \ "from_res").readNullable[String].map(_.map(KeyPair.fromAccountId)) and
      (JsPath \ "to_res").readNullable[String].map(_.map(KeyPair.fromAccountId))
    ){  (id: Option[Long], scheduled: ZonedDateTime, submitted: Option[ZonedDateTime], from: AccountIdLike,
  to: AccountIdLike, asset: String, units: Double, status: Payment.Status, result: Option[String],
         fromResolved: Option[PublicKeyOps], toResolved: Option[PublicKeyOps]) =>
      Payment(id, from, to, asset, None, units.toLong, scheduled, scheduled, submitted, status, result, fromResolved, toResolved)
  }

  implicit val paymentSubListReads: Reads[PaymentSubList] = (
    (JsPath \ "payments").read[Seq[Payment]] and
      (JsPath \ "total").readNullable[Int]
    )(PaymentSubList.apply _)

  implicit val arbPayments: Arbitrary[Seq[Payment]] = Arbitrary(Gen.listOf(genSuccessfulPayment))

  "GET listHistory" should {

    "return history window and total count" >> prop { (ps: Seq[Payment], total: Int) =>
      val paymentDao = mock[PaymentDao]
      paymentDao.history() returns ps.take(100)
      paymentDao.countHistoric returns total
      val controller = new PaymentsController(Stubs.stubSecurityComponents(), paymentDao)
      val result = controller.listHistory().apply(FakeRequest())
      val bodyText: String = contentAsString(result)
      val JsSuccess(paymentsSubList, _) = Json.fromJson[PaymentSubList](Json.parse(bodyText))

      paymentsSubList.total must beSome(total)
      paymentsSubList.payments must containTheSameElementsAs(ps.take(100).map(p => p.copy(received = p.scheduled)))
    }.setGen2(Gen.posNum[Int])
  }

  "GET listHistoryBefore" should {

    "return history window and no total count" >> prop { ps: Seq[Payment] =>
      val paymentDao = mock[PaymentDao]
      paymentDao.historyBefore(75) returns ps.take(100)
      val controller = new PaymentsController(Stubs.stubSecurityComponents(), paymentDao)
      val result = controller.listHistoryBefore(75).apply(FakeRequest())
      val bodyText: String = contentAsString(result)
      val JsSuccess(paymentsSubList, _) = Json.fromJson[PaymentSubList](Json.parse(bodyText))

      paymentsSubList.total must beNone
      paymentsSubList.payments must containTheSameElementsAs(ps.take(100).map(p => p.copy(received = p.scheduled)))
    }
  }

  "GET listHistoryAfter" should {

    "return history window and no total count" >> prop { ps: Seq[Payment] =>
      val paymentDao = mock[PaymentDao]
      paymentDao.historyAfter(75) returns ps.take(100)
      val controller = new PaymentsController(Stubs.stubSecurityComponents(), paymentDao)
      val result = controller.listHistoryAfter(75).apply(FakeRequest())
      val bodyText: String = contentAsString(result)
      val JsSuccess(paymentsSubList, _) = Json.fromJson[PaymentSubList](Json.parse(bodyText))

      paymentsSubList.total must beNone
      paymentsSubList.payments must containTheSameElementsAs(ps.take(100).map(p => p.copy(received = p.scheduled)))
    }
  }

  "GET listScheduled" should {

    "return scheduled payment window and total count" >> prop { (ps: Seq[Payment], total: Int) =>
      val paymentDao = mock[PaymentDao]
      paymentDao.scheduled() returns ps.take(100)
      paymentDao.countScheduled returns total
      val controller = new PaymentsController(Stubs.stubSecurityComponents(), paymentDao)
      val result = controller.listScheduled().apply(FakeRequest())
      val bodyText: String = contentAsString(result)
      val JsSuccess(paymentsSubList, _) = Json.fromJson[PaymentSubList](Json.parse(bodyText))

      paymentsSubList.total must beSome(total)
      paymentsSubList.payments must containTheSameElementsAs(ps.take(100).map(p => p.copy(received = p.scheduled)))
    }.setGen2(Gen.posNum[Int])
  }

  "GET listScheduledBefore" should {

    "return scheduled payment window and no count" >> prop { (ps: Seq[Payment], id: Int) =>
      val paymentDao = mock[PaymentDao]
      paymentDao.scheduledBefore(id) returns ps.take(100)
      val controller = new PaymentsController(Stubs.stubSecurityComponents(), paymentDao)
      val result = controller.listScheduledBefore(id).apply(FakeRequest())
      val bodyText: String = contentAsString(result)
      val JsSuccess(paymentsSubList, _) = Json.fromJson[PaymentSubList](Json.parse(bodyText))

      paymentsSubList.payments must containTheSameElementsAs(ps.take(100).map(p => p.copy(received = p.scheduled)))
    }.setGen2(Gen.posNum[Int])
  }

  "GET listScheduledAfter" should {

    "return scheduled payment window and no count" >> prop { (ps: Seq[Payment], id: Int) =>
      val paymentDao = mock[PaymentDao]
      paymentDao.scheduledAfter(id) returns ps.take(100)
      val controller = new PaymentsController(Stubs.stubSecurityComponents(), paymentDao)
      val result = controller.listScheduledAfter(id).apply(FakeRequest())
      val bodyText: String = contentAsString(result)
      val JsSuccess(paymentsSubList, _) = Json.fromJson[PaymentSubList](Json.parse(bodyText))

      paymentsSubList.payments must containTheSameElementsAs(ps.take(100).map(p => p.copy(received = p.scheduled)))
    }.setGen2(Gen.posNum[Int])
  }
}
