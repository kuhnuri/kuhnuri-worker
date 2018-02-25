package controllers.v1

import javax.inject._

import models._
import models.Job._
import models.ConversionStatus._
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.mvc._
import services.{Worker, WorkerService}

@Singleton
class WorkerController @Inject()(workerService: WorkerService, cc: ControllerComponents) extends AbstractController(cc) {

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
