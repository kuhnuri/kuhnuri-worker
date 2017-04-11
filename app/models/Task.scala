package models

import java.net.URI

import play.api.libs.functional.syntax._
import play.api.libs.json._
import controllers.v1.WorkerController.jobWrites

sealed case class Task(input: URI, output: URI, job: Job)

object Task {
  implicit val uriWrites: Writes[URI] = Writes { uri => JsString(uri.toString) }

  implicit val taskWrites: Writes[Task] = (
    (JsPath \ "input").write[URI] and
      (JsPath \ "output").write[URI] and
      (JsPath \ "job").write[Job]
    ) (unlift(Task.unapply _))
}
