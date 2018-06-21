package models

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
sealed case class Work(input: Path, output: Path, task: Task)

object Work {
  implicit val pathWrites: Writes[Path] = Writes { path => JsString(path.toString) }

  implicit val taskWrites: Writes[Work] = (
    (JsPath \ "input").write[Path] and
      (JsPath \ "output").write[Path] and
      (JsPath \ "task").write[Task]
    ) (unlift(Work.unapply _))

  implicit val pathReads = Reads[Path](j => try {
    JsSuccess(Paths.get(j.as[JsString].value))
  } catch {
    case e: InvalidPathException => JsError(e.toString)
  })

  implicit val taskReads: Reads[Work] = (
    (JsPath \ "input").read[Path] and
      (JsPath \ "output").read[Path] and
      (JsPath \ "task").read[Task]
    ) (Work.apply _)
}
