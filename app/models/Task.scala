package models

import java.net.{URI, URISyntaxException}

import play.api.libs.functional.syntax._
import play.api.libs.json._
import models.Job.jobWrites
import models.Job.jobReads

sealed case class Task(input: URI, output: URI, job: Job)

object Task {
  implicit val uriWrites: Writes[URI] = Writes { uri => JsString(uri.toString) }

  implicit val taskWrites: Writes[Task] = (
    (JsPath \ "input").write[URI] and
      (JsPath \ "output").write[URI] and
      (JsPath \ "job").write[Job]
    ) (unlift(Task.unapply _))

  implicit val uriReads = Reads[URI](j => try {
      JsSuccess(new URI(j.as[JsString].value))
    } catch {
      case e: URISyntaxException  => JsError(e.toString)
    })

  implicit val taskReads: Reads[Task] = (
    (JsPath \ "input").read[URI] and
      (JsPath \ "output").read[URI] and
      (JsPath \ "job").read[Job]
    ) (Task.apply _)
}
