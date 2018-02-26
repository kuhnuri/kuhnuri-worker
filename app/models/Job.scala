package models

import java.time.{LocalDateTime, ZoneOffset, ZonedDateTime}

import play.api.libs.functional.syntax._
import play.api.libs.json._

sealed case class Job(id: String, input: String, output: String, transtype: String,
                      params: Map[String, String], status: StatusString, priority: Int,
                      created: LocalDateTime, processing: Option[LocalDateTime], finished: Option[LocalDateTime]) {
  //  def toJobStatus(status: StatusString): JobStatus = {
  //    JobStatus(id, output, status)
  //  }
}

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
      (JsPath \ "processing").writeNullable[LocalDateTime] and
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
      (JsPath \ "processing").readNullable[LocalDateTime] and
      (JsPath \ "finished").readNullable[LocalDateTime]
    ) (Job.apply _)
}

sealed trait StatusString

object StatusString {

  case object Queue extends StatusString {
    override val toString = "queue"
  }

  case object Process extends StatusString {
    override val toString = "process"
  }

  case object Done extends StatusString {
    override val toString = "done"
  }

  case object Error extends StatusString {
    override val toString = "error"
  }

  def parse(status: String): StatusString = status match {
    case "queue" => Queue
    case "process" => Process
    case "done" => Done
    case "error" => Error
    case s: String => throw new IllegalArgumentException(s"Unsupported status value: ${s}")
  }

  implicit val jobStatusStringReads: Reads[StatusString] =
    Reads[StatusString](j => try {
      JsSuccess(StatusString.parse(j.as[JsString].value))
    } catch {
      case e: IllegalArgumentException => JsError(e.toString)
    })

  implicit val jobStatusStringWrites: Writes[StatusString] =
    Writes[StatusString](s => JsString(s.toString))

}
