package models

import java.net.{URI, URISyntaxException}

import play.api.libs.functional.syntax._
import play.api.libs.json._

sealed case class WorkerTask(input: Option[URI], output: Option[URI], retry: Boolean, task: Task)

object WorkerTask {

  import models.Task.{taskReads, taskWrites}

  implicit val uriWrites: Writes[URI] = Writes { uri => JsString(uri.toString) }

  implicit val workerTaskWrites: Writes[WorkerTask] = (
    (JsPath \ "input").writeNullable[URI] and
      (JsPath \ "output").writeNullable[URI] and
      (JsPath \ "retry").write[Boolean] and
      (JsPath \ "task").write[Task]
    ) (unlift(WorkerTask.unapply _))

  implicit val uriReads = Reads[URI](j => try {
    JsSuccess(new URI(j.as[JsString].value))
  } catch {
    case e: URISyntaxException => JsError(e.toString)
  })

  implicit val workerTaskReads: Reads[WorkerTask] = (
    (JsPath \ "input").readNullable[URI] and
      (JsPath \ "output").readNullable[URI] and
      (JsPath \ "retry").read[Boolean] and
      (JsPath \ "task").read[Task]
    ) (WorkerTask.apply _)
}
