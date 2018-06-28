package models

import java.net.{URI, URISyntaxException}
import java.nio.file.{InvalidPathException, Path, Paths}

import play.api.libs.functional.syntax._
import play.api.libs.json._

/**
  * Work item for a task.
  *
  * @param input  temporary input resource
  * @param output temporary output resource
  * @param task   task to process
  */
sealed case class Work(input: URI, output: URI, task: Task, temp: Path)

object Work {
  implicit val uriWrites: Writes[URI] = Writes { path => JsString(path.toString) }

  implicit val pathWrites: Writes[Path] = Writes { path => JsString(path.toString) }

  implicit val taskWrites: Writes[Work] = (
    (JsPath \ "input").write[URI] and
      (JsPath \ "output").write[URI] and
      (JsPath \ "task").write[Task] and
      (JsPath \ "temp").write[Path]
    ) (unlift(Work.unapply _))

  implicit val uriReads = Reads[URI](j => try {
    JsSuccess(new URI(j.as[JsString].value))
  } catch {
    case e: URISyntaxException => JsError(e.toString)
  })

  implicit val pathReads = Reads[Path](j => try {
    JsSuccess(Paths.get(j.as[JsString].value))
  } catch {
    case e: InvalidPathException => JsError(e.toString)
  })

  implicit val taskReads: Reads[Work] = (
    (JsPath \ "input").read[URI] and
      (JsPath \ "output").read[URI] and
      (JsPath \ "task").read[Task] and
      (JsPath \ "temp").read[Path]
    ) (Work.apply _)
}
