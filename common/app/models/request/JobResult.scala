package models.request

import models.Task
import play.api.libs.functional.syntax.{unlift, _}
import play.api.libs.json._

sealed case class JobResult(job: Task, log: Seq[String])

object JobResult {
  implicit val jobResultWrites: Writes[JobResult] = (
    (JsPath \ "task").write[Task] and
      (JsPath \ "log").write[Seq[String]]
    ) (unlift(JobResult.unapply _))
}