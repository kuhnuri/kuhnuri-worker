package models

import play.api.libs.functional.syntax.unlift
import play.api.libs.functional.syntax._
import play.api.libs.json._

sealed case class JobResult(job: Job, log: Seq[String])

object JobResult {
  implicit val jobResultWrites: Writes[JobResult] = (
    (JsPath \ "job").write[Job] and
      (JsPath \ "log").write[Seq[String]]
    ) (unlift(JobResult.unapply _))
}