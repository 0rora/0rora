package controllers

import java.time.{Instant, ZoneId, ZonedDateTime}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import controllers.actions.AuthenticatedUserAction
import models.Generators.genSuccessfulPayment
import models.Global.SessionUsernameKey
import models.repo.{Payment, PaymentRepo}
import org.scalacheck.{Arbitrary, Gen}
import org.specs2.ScalaCheck
import org.specs2.concurrent.ExecutionEnv
import org.specs2.execute.Results
import org.specs2.mock.Mockito
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json.{JsPath, Json, Reads, _}
import play.api.mvc.BodyParsers
import play.api.test._
import stellar.sdk.{KeyPair, PublicKeyOps}


class PaymentsControllerSpec(implicit ec: ExecutionEnv) extends PlaySpecification with Results with Mockito with ScalaCheck {
  implicit val sys: ActorSystem = ActorSystem("PaymentsControllerSpec")
  implicit val mat: ActorMaterializer = ActorMaterializer()
  private val authUserAction = new AuthenticatedUserAction(new BodyParsers.Default())

  private def epochMillisToUTCDateTime(m: Long) =
    ZonedDateTime.ofInstant(Instant.ofEpochMilli(m), ZoneId.of("UTC"))

  implicit val paymentReads: Reads[Payment] = (
    (JsPath \ "id").readNullable[Long] and
      (JsPath \ "scheduled").read[Long].map(epochMillisToUTCDateTime) and
      (JsPath \ "submitted").readNullable[Long].map(_.map(epochMillisToUTCDateTime)) and
      (JsPath \ "from").read[String].map(KeyPair.fromAccountId) and
      (JsPath \ "to").read[String].map(KeyPair.fromAccountId) and
      (JsPath \ "asset").read[String] and
      (JsPath \ "units").read[Double].map(BigDecimal.apply).map(_ * 10000000.0).map(_.doubleValue) and
      (JsPath \ "status").read[String].map(Payment.status) and
      (JsPath \ "result").readNullable[String]
    ){  (id: Option[Long], scheduled: ZonedDateTime, submitted: Option[ZonedDateTime], from: PublicKeyOps,
  to: PublicKeyOps, asset: String, units: Double, status: Payment.Status, result: Option[String]) =>
      Payment(id, from, to, asset, None, units.toLong, scheduled, scheduled, submitted, status, result)
  }

  implicit val paymentSubListReads: Reads[PaymentSubList] = (
    (JsPath \ "payments").read[Seq[Payment]] and
      (JsPath \ "total").readNullable[Int]
    )(PaymentSubList.apply _)

  implicit val arbPayments: Arbitrary[Seq[Payment]] = Arbitrary(Gen.listOf(genSuccessfulPayment))

  "GET listHistory" should {

    "return history window and total count" >> prop { (ps: Seq[Payment], total: Int) =>
      val paymentRepo = mock[PaymentRepo]
      paymentRepo.history() returns ps.take(100)
      paymentRepo.countHistoric returns total
      val controller = new PaymentsController(Stubs.stubMessagesControllerComponents(), authUserAction, paymentRepo)
      val result = controller.listHistory().apply(FakeRequest().withSession(SessionUsernameKey -> "anyone"))
      val bodyText: String = contentAsString(result)
      val JsSuccess(paymentsSubList, _) = Json.fromJson[PaymentSubList](Json.parse(bodyText))

      paymentsSubList.total must beSome(total)
      paymentsSubList.payments must containTheSameElementsAs(ps.take(100).map(p => p.copy(received = p.scheduled)))
    }.setGen2(Gen.posNum[Int])
  }

  "GET listHistoryBefore" should {

    "return history window and no total count" >> prop { ps: Seq[Payment] =>
      val paymentRepo = mock[PaymentRepo]
      paymentRepo.historyBefore(75) returns ps.take(100)
      val controller = new PaymentsController(Stubs.stubMessagesControllerComponents(), authUserAction, paymentRepo)
      val result = controller.listHistoryBefore(75).apply(FakeRequest().withSession(SessionUsernameKey -> "anyone"))
      val bodyText: String = contentAsString(result)
      val JsSuccess(paymentsSubList, _) = Json.fromJson[PaymentSubList](Json.parse(bodyText))

      paymentsSubList.total must beNone
      paymentsSubList.payments must containTheSameElementsAs(ps.take(100).map(p => p.copy(received = p.scheduled)))
    }
  }

  "GET listHistoryAfter" should {

    "return history window and no total count" >> prop { ps: Seq[Payment] =>
      val paymentRepo = mock[PaymentRepo]
      paymentRepo.historyAfter(75) returns ps.take(100)
      val controller = new PaymentsController(Stubs.stubMessagesControllerComponents(), authUserAction, paymentRepo)
      val result = controller.listHistoryAfter(75).apply(FakeRequest().withSession(SessionUsernameKey -> "anyone"))
      val bodyText: String = contentAsString(result)
      val JsSuccess(paymentsSubList, _) = Json.fromJson[PaymentSubList](Json.parse(bodyText))

      paymentsSubList.total must beNone
      paymentsSubList.payments must containTheSameElementsAs(ps.take(100).map(p => p.copy(received = p.scheduled)))
    }
  }

  "GET listScheduled" should {

    "return scheduled payment window and total count" >> prop { (ps: Seq[Payment], total: Int) =>
      val paymentRepo = mock[PaymentRepo]
      paymentRepo.scheduled() returns ps.take(100)
      paymentRepo.countScheduled returns total
      val controller = new PaymentsController(Stubs.stubMessagesControllerComponents(), authUserAction, paymentRepo)
      val result = controller.listScheduled().apply(FakeRequest().withSession(SessionUsernameKey -> "anyone"))
      val bodyText: String = contentAsString(result)
      val JsSuccess(paymentsSubList, _) = Json.fromJson[PaymentSubList](Json.parse(bodyText))

      paymentsSubList.total must beSome(total)
      paymentsSubList.payments must containTheSameElementsAs(ps.take(100).map(p => p.copy(received = p.scheduled)))
    }.setGen2(Gen.posNum[Int])
  }
}
