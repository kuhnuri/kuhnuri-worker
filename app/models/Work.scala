package models

import java.net.{URI, URISyntaxException}

import play.api.libs.functional.syntax._
import play.api.libs.json._
import models.Task.taskWrites
import models.Task.taskReads

/**
  * Work item for a task.
  */
sealed case class Work(input: URI, output: URI, task: Task)

object Work {
  implicit val uriWrites: Writes[URI] = Writes { uri => JsString(uri.toString) }

  implicit val taskWrites: Writes[Work] = (
    (JsPath \ "input").write[URI] and
      (JsPath \ "output").write[URI] and
      (JsPath \ "task").write[Task]
    ) (unlift(Work.unapply _))

  implicit val uriReads = Reads[URI](j => try {
      JsSuccess(new URI(j.as[JsString].value))
    } catch {
      case e: URISyntaxException  => JsError(e.toString)
    })

  implicit val taskReads: Reads[Work] = (
    (JsPath \ "input").read[URI] and
      (JsPath \ "output").read[URI] and
      (JsPath \ "task").read[Task]
    ) (Work.apply _)
}
