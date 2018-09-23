package controllers

import java.nio.charset.Charset
import java.nio.file.{Files => FS}

import javax.inject.Inject
import play.api.libs.Files
import play.api.mvc._

import scala.collection.JavaConverters._

class SourcesController @Inject()(cc: MessagesControllerComponents) extends MessagesAbstractController(cc) {

  def uploadCSV: Action[Files.TemporaryFile] = Action(parse.temporaryFile) { request =>
    val path = request.body.path
    FS.lines(path, Charset.forName("UTF-8")).iterator().asScala.foreach(println)
    Ok("File uploaded")
  }

}
