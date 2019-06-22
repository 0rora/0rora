package controllers

import java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME
import java.time.{ZoneId, ZonedDateTime}

import akka.Done
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink}
import models.Generators._
import models.db.PaymentDao
import models.{Payment, PaymentProcessor}
import org.scalacheck.Gen
import org.specs2.concurrent.ExecutionEnv
import org.specs2.execute.Results
import org.specs2.mock.Mockito
import play.api.libs.Files
import play.api.libs.Files.TemporaryFile
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{Headers, MultipartFormData, Result}
import play.api.test.{FakeRequest, PlaySpecification}

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._

class SourcesControllerSpec(implicit ec: ExecutionEnv) extends PlaySpecification with Results with Mockito {

  implicit val sys: ActorSystem = ActorSystem("SourcesControllerSpec")
  implicit val mat: ActorMaterializer = ActorMaterializer()
  private val now = ZonedDateTime.now()

  "sources controller" should {
    "write payments to the repo" >> {
      val payments: List[Payment] = sampleOf(Gen.listOfN(20, genScheduledPayment)).map(_.copy(id = None, received = now))
      val content = payments.map{ p =>
        s"${p.source.account},${p.destination.account},XLM,,${p.units},${p.scheduled.format(ISO_OFFSET_DATE_TIME)}"
      }.mkString(System.lineSeparator())
      val eventualResult = result(content)
      val json = contentAsJson(eventualResult.map(_._1))
      val paymentsSunk = eventualResult.map(_._2.map(p => p.copy(received = now, scheduled = p.scheduled.withZoneSameInstant(ZoneId.of("UTC")))))

      paymentsSunk must containTheSameElementsAs(payments).awaitFor(30 seconds)
      status(eventualResult.map(_._1)) mustEqual 200
      (json \ "success").as[Boolean] must beTrue
      (json \ "count").as[Int] mustEqual 20
    }

    "ignore bad lines in the csv input" >> {
      val payments: List[Payment] = sampleOf(Gen.listOfN(14, genScheduledPayment)).map(_.copy(id = None, received = now))
      val rubbish: List[String] = sampleOf(Gen.listOfN(14, Gen.identifier))
      def interleave(l: Seq[String], r: Seq[String], acc: Seq[String] = Nil): Seq[String] = l match {
        case h +: t if r.nonEmpty => interleave(t, r.tail, h +: r.head +: acc)
        case _ => acc
      }
      val content = interleave(payments.map{ p =>
        s"${p.source.account},${p.destination.account},XLM,,${p.units},${p.scheduled.format(ISO_OFFSET_DATE_TIME)}"
      }, rubbish).mkString(System.lineSeparator())
      val eventualResult = result(content)
      val json = contentAsJson(eventualResult.map(_._1))
      val paymentsSunk = eventualResult.map(_._2.map(p => p.copy(received = now, scheduled = p.scheduled.withZoneSameInstant(ZoneId.of("UTC")))))

      paymentsSunk must containTheSameElementsAs(payments).awaitFor(30 seconds)
      status(eventualResult.map(_._1)) mustEqual 200
      (json \ "success").as[Boolean] must beTrue
      (json \ "count").as[Int] mustEqual 14
    }
  }

  private def result(content: String): Future[(Result, Seq[Payment], Boolean)] = {
    val repo = mock[PaymentDao]
    val flows = mutable.Buffer.empty[Payment]
    val writer: Sink[Payment, Future[Done]] = Flow[Payment].toMat(Sink.foreach { p: Payment =>
      flows += p
    })(Keep.right)
    repo.writer returns writer

    val processor = mock[PaymentProcessor]
    val controller = new SourcesController(Stubs.stubMessagesControllerComponents(), repo, processor)

    val f: TemporaryFile = Files.SingletonTemporaryFileCreator.create("sources-controller-spec", "csv")
    f.deleteOnExit()
    java.nio.file.Files.write(f.path, content.getBytes("UTF-8"))

    val part = FilePart[TemporaryFile](
      key = "csv_file",
      filename = "payments.csv",
      contentType = Some("text/plain"),
      ref = f
    )

    val request: FakeRequest[MultipartFormData[TemporaryFile]] = FakeRequest("POST", "/sources/csv", Headers(),
      body = MultipartFormData(Map.empty, Seq(part), Nil))

    controller.uploadCSV().apply(request).map(r => (r, flows, there was one(processor).checkForPayments()))
  }

}
