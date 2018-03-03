package services

import java.net.{ConnectException, URI, UnknownHostException}

import filters.TokenAuthorizationFilter
import filters.TokenAuthorizationFilter._
import javax.inject.Inject
import models.Register._
import models._
import play.Environment
import play.api.http.Status.{NO_CONTENT, OK, UNAUTHORIZED}
import play.api.libs.json._
import play.api.libs.ws.{WSClient, WSRequest}
import play.api.{Configuration, Logger}

import scala.collection.immutable.List
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
  * Polls a Queue for jobs and submits them back after processing.
  */
trait Poller {

  def status: ConversionStatus

  def submitResults(res: Try[Task]): Future[Try[Task]]

  def getWork(): Future[Try[Job]]

  def unregister(): Future[Unit]

}

class RestPoller @Inject()(implicit context: ExecutionContext, ws: WSClient,
                           configuration: Configuration, environment: Environment) extends Poller {

  private val logger = Logger(this.getClass)

  private val queueBaseUrl = configuration.get[String]("queue.url")
  private val idleDuration = configuration.getMillis("worker.idle")
  private val registerUrl = s"${queueBaseUrl}api/v1/login"
  private val unregisterUrl = s"${queueBaseUrl}api/v1/logout"
  private val requestUrl = s"${queueBaseUrl}api/v1/work"
  private val submitUrl = s"${queueBaseUrl}api/v1/work"

  private val workerId = configuration.get[String]("worker.id")
  private val workerBaseUrl = new URI(configuration.get[String]("worker.url"))

  protected val transtypes = readTranstypes
  protected var currentStatus: ConversionStatus = Busy()
  // FIXME What to do here, wait in loop until we can register?
  TokenAuthorizationFilter.authToken = Option.empty //register().toOption
  logger.debug(s"Token: ${TokenAuthorizationFilter.authToken}")

  private def getToken(): Future[Try[String]] = {
    if (authToken.isDefined) {
      Future(Success(authToken.get))
    } else {
      val register = Register(workerId, workerBaseUrl)
      logger.info(s"Register to queue $registerUrl as $register")
      ws.url(registerUrl)
        .withRequestTimeout(10000.millis)
        .post(Json.toJson(register))
        .map { response =>
          response.status match {
            case OK => {
              logger.info("Registration to queue done")
              response.header(AUTH_TOKEN_HEADER)
                .map(token => {
                  TokenAuthorizationFilter.authToken = Option(token)
                  Success(token)
                })
                .getOrElse(Failure(new UnavailableException("Token not available after registration")))
            }
            // FIXME: don't throw, return Failure
            case UNAUTHORIZED =>
              TokenAuthorizationFilter.authToken = Option.empty
              Failure(new UnauthorizedException(s"Unauthorized, login: ${response.status}"))
            case code => throw new IllegalArgumentException(s"Unsupported response $code, login: ${response.status}")
          }
        }
        .recover {
          case e: UnknownHostException =>
            Failure(new UnavailableException("Registration unavailable after timeout"))
          case e: ConnectException =>
            Failure(new UnavailableException("Registration unavailable after timeout"))
          case e: Throwable =>
            // FIXME: don't throw, return Failure
            throw new IllegalStateException(s"Unable to register: ${e.getMessage}", e)
        }
    }
  }

  override def unregister(): Future[Unit] = {
    logger.info(s"unregister: ${TokenAuthorizationFilter.authToken}")
    ws.url(unregisterUrl)
      .addHttpHeaders(AUTH_TOKEN_HEADER -> authToken.get)
      .withRequestTimeout(10000.millis)
      .post("")
      .map { response =>
        response.status match {
          case OK =>
            logger.info("Successfully unregistered")
            ()
          case UNAUTHORIZED =>
            TokenAuthorizationFilter.authToken = Option.empty
            throw new IllegalArgumentException(s"Unauthorized, login: ${response.status}")
          case code =>
            throw new IllegalArgumentException(s"Unsupported response $code, login: ${response.status}")
        }
      }
  }

  private def readTranstypes: Seq[String] = {
    configuration.get[Seq[String]]("worker.transtypes")
  }

  override def status: ConversionStatus = currentStatus

  /**
    * Request work from Queue.
    */
  def getWork(): Future[Try[Job]] = {
    getToken().flatMap {
      case Success(token) => {
        //    lock()
        val request: WSRequest = ws.url(requestUrl)
        logger.debug(s"Get work ${request.uri}")
        val complexRequest: WSRequest = request
          .addHttpHeaders(
            "Accept" -> "application/json",
            AUTH_TOKEN_HEADER -> token)
          .withRequestTimeout(10000.millis)
        val res: Future[Try[Job]] = complexRequest.post(Json.toJson(transtypes))
          .flatMap { response =>
            response.status match {
              case OK =>
                response.json.validate[Job]
                  .map {
                    case job => Future(Success(job))
                  }
                  .recoverTotal {
                    e => Future(Failure(new IllegalArgumentException(s"Invalid queue response: ${JsError.toJson(e)}")))
                  }
              case NO_CONTENT =>
                idle()
              case UNAUTHORIZED =>
                TokenAuthorizationFilter.authToken = Option.empty
                Future(Failure(new UnauthorizedException(s"Unauthorized, login: ${response.status}")))
              case _ =>
                Future(Failure(new IllegalArgumentException(s"Got unexpected response from queue: ${response.status}")))
            }
          }
          .recover {
            case e: UnknownHostException =>
//              e.printStackTrace()
              Failure(new UnavailableException(s"Failed to submit: ${e.getMessage}"))
            case e: ConnectException =>
//              e.printStackTrace()
              Failure(new UnavailableException(s"Failed to submit: ${e.getMessage}"))
            case e: Throwable => {
              Failure(new Exception(s"Failed to request: ${e.getMessage}", e))
            }
          }
        res
      }
      case Failure(e) => {
        Future(Failure(e))
      }
    }
  }

  def submitResults(res: Try[Task]): Future[Try[Task]] = {
    logger.debug("Submit: " + res)
    val f = res match {
      case Success(task) =>
        submitJob(task.job.copy(status = StatusString.Done), res)
      case f@Failure(NoWorkException()) =>
        Future(f)
      case f@Failure(UnauthorizedException(_)) =>
        Future(f)
      case f@Failure(UnavailableException(_)) =>
        Future(f)
      case Failure(ProcessorException(_, job)) =>
        submitJob(job.copy(status = StatusString.Error), res)
      case _@t =>
        throw new IllegalArgumentException("Unknown failure type: " + t.toString)
    }
    //    f.onComplete {
    //      case _ => unlock()
    //    }
    f
  }

  private def submitJob(job: Job, res: Try[Task]): Future[Try[Task]] = {
    // FIXME
    val log = List() //cacheListener.messages.map(_.msg.toString)
    //        cacheListener.messages.clear()
    val otRes: JobResult = JobResult(job, log)

    val request: WSRequest = ws.url(submitUrl)
    val complexRequest: WSRequest = request
      .addHttpHeaders(
        AUTH_TOKEN_HEADER -> authToken.get)
      .withRequestTimeout(10000.millis)
    logger.debug(s"Submit ${otRes.job.id} results to queue")
    complexRequest
      .put(Json.toJson(otRes))
      .map { response =>
        response.status match {
          case OK =>
            res
          case UNAUTHORIZED =>
            TokenAuthorizationFilter.authToken = Option.empty
            Failure(new IllegalStateException(s"Unauthorized, login: ${response.status}"))
          case _ =>
            Failure(new IllegalArgumentException(s"Submit responded with ${response.status}"))
        }
      }
      .recover {
        //            case e: UnknownHostException => {
        //            case e: ConnectException => {
        case e: Throwable => {
          //              e.printStackTrace()
          Failure(new Exception("Failed to submit: " + e.getMessage, e))
        }
      }
  }

  private def idle(): Future[Try[Job]] = {
    //    assert(currentStatus.isInstanceOf[Free])
    Future {
      logger.debug(s"Idle for ${idleDuration}ms")
      unlock()
      Thread.sleep(idleDuration)
      lock()
      Failure(new NoWorkException)
    }
  }

  private def lock(): Future[ConversionStatus] = Future {
    //    logger.info(s"Lock: current status ${currentStatus} = ${currentStatus.isInstanceOf[Free]}")
    //    assert(currentStatus.isInstanceOf[Free])
    currentStatus = Busy()
    currentStatus
  }

  private def unlock(): Future[ConversionStatus] = Future {
    //    logger.info(s"Unlock: current status ${currentStatus} = ${currentStatus.isInstanceOf[Busy]}")
    assert(currentStatus.isInstanceOf[Busy])
    currentStatus = Free()
    currentStatus
  }

}

private case class NoWorkException() extends Exception()

private case class UnauthorizedException(msg: String) extends Exception(msg)

private case class UnavailableException(msg: String) extends Exception(msg)
