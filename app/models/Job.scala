package models

import java.time.{LocalDateTime, ZoneOffset, ZonedDateTime}

import play.api.libs.functional.syntax._
import play.api.libs.json._

sealed case class Job(id: String,
                      input: String,
                      output: String,
                      transtype: String,
                      params: Map[String, String],
                      status: StatusString,
                      priority: Int,
                      created: LocalDateTime,
                      processing: LocalDateTime,
                      worker: String,
                      finished: Option[LocalDateTime])

object Job {

  import models.StatusString.{jobStatusStringReads, jobStatusStringWrites}

  implicit val localDateTimeWrites =
    Writes[LocalDateTime](s => JsString(s.atOffset(ZoneOffset.UTC).toString))


  implicit val localDateTimeReads =
    Reads[LocalDateTime](j => try {
      JsSuccess(ZonedDateTime.parse(j.as[JsString].value).toLocalDateTime)
    } catch {
      case e: IllegalArgumentException => JsError(e.toString)
    })

  implicit val jobWrites: Writes[Job] = (
    (JsPath \ "id").write[String] and
      (JsPath \ "input").write[String] and
      (JsPath \ "output").write[String] and
      (JsPath \ "transtype").write[String] and
      (JsPath \ "params").write[Map[String, String]] and
      (JsPath \ "status").write[StatusString] and
      (JsPath \ "priority").write[Int] and
      (JsPath \ "created").write[LocalDateTime] and
      (JsPath \ "processing").write[LocalDateTime] and
      (JsPath \ "worker").write[String] and
      (JsPath \ "finished").writeNullable[LocalDateTime]
    ) (unlift(Job.unapply _))
  implicit val jobReads: Reads[Job] = (
    (JsPath \ "id").read[String] and
      (JsPath \ "input").read[String] /*.filter(new URI(_).isAbsolute)*/ and
      (JsPath \ "output").read[String] /*.filter(_.map {
        new URI(_).isAbsolute
      }.getOrElse(true))*/ and
      (JsPath \ "transtype").read[String] and
      (JsPath \ "params").read[Map[String, String]] and
      (JsPath \ "status").read[StatusString] and
      (JsPath \ "priority").read[Int] and
      (JsPath \ "created").read[LocalDateTime] and
      (JsPath \ "processing").read[LocalDateTime] and
      (JsPath \ "worker").read[String] and
      (JsPath \ "finished").readNullable[LocalDateTime]
    ) (Job.apply _)
}
