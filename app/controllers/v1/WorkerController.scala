package controllers.v1

import javax.inject._

import models._
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.mvc._
import services.{Worker, WorkerService}

@Singleton
class WorkerController @Inject()(workerService: WorkerService) extends Controller {

  import controllers.v1.WorkerController._

  def status = Action {
    Ok(Json.toJson(workerService.status))
  }

  def log(offset: Int) = Action {
    Ok(Json.toJson(workerService.log(offset)))
  }

  //  def cancel = {
  //    Ok(Json.toJson(worker.cancel))
  //  }

  //
  //  def request = Action.async { request =>
  //    request.body.asJson.map { json =>
  //      json.validate[List[String]].map {
  //        case req: List[String] => {
  //          Future {
  //            worker.request(req)
  //          }.map {
  //            case Some(job) => Ok(Json.toJson(job))
  //            case None => NoContent
  //          }
  //        }
  //      }.recoverTotal {
  //        e => Future(BadRequest("Detected error:" + JsError.toJson(e)))
  //      }
  //    }.getOrElse {
  //      Future(BadRequest("Expecting Json data"))
  //    }
  //  }
  //
  //  // FIXME this doesn't have to return anything, other than OK/NOK
  //  def submit = Action.async { request =>
  //    request.body.asJson.map { json =>
  //      json.validate[Job].map {
  //        case job: Job => {
  //          Future {
  //            worker.submit(job)
  //          }.map {
  //            case res => Ok(Json.toJson(res))
  //          }
  //        }
  //      }.recoverTotal {
  //        e => Future(BadRequest("Detected error:" + JsError.toJson(e)))
  //      }
  //    }.getOrElse {
  //      Future(BadRequest("Expecting Json data"))
  //    }
  //  }

}

object WorkerController {

  implicit val jobStatusStringReads =
    Reads[StatusString](j => try {
      JsSuccess(StatusString.parse(j.as[JsString].value))
    } catch {
      case e: IllegalArgumentException => JsError(e.toString)
    })

  implicit val jobStatusStringWrites =
    Writes[StatusString](s => JsString(s.toString))

//  implicit val createReads: Reads[Create] = (
//    (JsPath \ "input").read[String] /*.filter(new URI(_).isAbsolute)*/ and
//      (JsPath \ "output").readNullable[String] /*.filter(_.map {
//        new URI(_).isAbsolute
//      }.getOrElse(true))*/ and
//      (JsPath \ "transtype").read[String] and
//      (JsPath \ "params").read[Map[String, String]]
//    ) (Create.apply _)

  implicit val jobWrites: Writes[Job] = (
    (JsPath \ "id").write[String] and
      (JsPath \ "input").write[String] and
      (JsPath \ "output").write[String] and
      (JsPath \ "transtype").write[String] and
      (JsPath \ "params").write[Map[String, String]] and
      (JsPath \ "status").write[StatusString]
    ) (unlift(Job.unapply _))
  implicit val jobReads: Reads[Job] = (
    (JsPath \ "id").read[String] and
      (JsPath \ "input").read[String] /*.filter(new URI(_).isAbsolute)*/ and
      (JsPath \ "output").read[String] /*.filter(_.map {
        new URI(_).isAbsolute
      }.getOrElse(true))*/ and
      (JsPath \ "transtype").read[String] and
      (JsPath \ "params").read[Map[String, String]] and
      (JsPath \ "status").read[StatusString]
    ) (Job.apply _)

  implicit val jobStatusWrites: Writes[models.ConversionStatus] =
    Writes[models.ConversionStatus](s => JsObject(Map(
      "status" -> (s match {
        case Free() => JsString("free")
        case Busy() => JsString("busy")
      })
    )))

  implicit val jobResultWrites: Writes[JobResult] = (
    (JsPath \ "job").write[Job] and
      (JsPath \ "log").write[Seq[String]]
    ) (unlift(JobResult.unapply _))

}
