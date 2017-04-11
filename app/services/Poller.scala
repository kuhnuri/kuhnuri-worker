package services

import java.net.URI
import javax.inject.Inject

import controllers.v1.WorkerController._
import filters.TokenAuthorizationFilter
import filters.TokenAuthorizationFilter._
import models.Register._
import models._
import play.Environment
import play.api.http.Status.{NO_CONTENT, OK, UNAUTHORIZED}
import play.api.libs.json._
import play.api.libs.ws.{WSClient, WSRequest}
import play.api.{Configuration, Logger}

import scala.collection.JavaConversions._
import scala.collection.immutable.List
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

trait Poller {

  def status: ConversionStatus

  def submitResults(res: Try[Task]): Future[Try[Task]]

  def getWork(): Future[Try[Job]]

  def unregister(): Future[Unit]

}

class RestPoller @Inject()(implicit context: ExecutionContext, ws: WSClient,
                           configuration: Configuration, environment: Environment) extends Poller {

  private val logger = Logger(this.getClass)

  private val queueBaseUrl = configuration.getString("queue.url").get
  private val idleDuration = configuration.getMilliseconds("worker.idle").get
  private val registerUrl = s"${queueBaseUrl}api/v1/login"
  private val unregisterUrl = s"${queueBaseUrl}api/v1/logout"
  private val requestUrl = s"${queueBaseUrl}api/v1/work"
  private val submitUrl = s"${queueBaseUrl}api/v1/work"

  private val workerBaseUrl = new URI(configuration.getString("worker.url").get)

  protected val transtypes = readTranstypes
  protected var currentStatus: ConversionStatus = Busy()
  TokenAuthorizationFilter.authToken = register()
  logger.info(s"Token: ${TokenAuthorizationFilter.authToken}")

  // FIXME add user/password
  private def register(): Option[String] = {
    val register = Register(workerBaseUrl)
    logger.info(s"Register to queue: $register")
    val request = ws.url(registerUrl)
      .withRequestTimeout(10000.millis)
      .post(Json.toJson(register))
    val response = Await.result(request, 10000.millis)
    response.status match {
      case OK => response.header(AUTH_TOKEN_HEADER)
      case UNAUTHORIZED => throw new IllegalArgumentException(s"Unauthorized, login: ${response.status}")
      case code => throw new IllegalArgumentException(s"Unsupported response $code, login: ${response.status}")
    }
  }

  override def unregister(): Future[Unit] = {
    logger.info(s"unregister: ${TokenAuthorizationFilter.authToken}")
    ws.url(unregisterUrl)
      .withHeaders(AUTH_TOKEN_HEADER -> authToken.get)
      .withRequestTimeout(10000.millis)
      .post("")
      .map { response =>
        response.status match {
          case OK =>
            logger.info("Successfully unregistered")
            ()
          case UNAUTHORIZED =>
            throw new IllegalArgumentException(s"Unauthorized, login: ${response.status}")
          case code =>
            throw new IllegalArgumentException(s"Unsupported response $code, login: ${response.status}")
        }
      }
  }

  private def readTranstypes: List[String] = {
    configuration.getStringList("worker.transtypes").get.toList
  }

  //  override def run(): Future[Unit] = {
  //    if (shutdownPromise) {
  //      logger.debug("Shutdown requested, return immediately")
  //      Future(())
  //    } else {
  //      val f: Future[Try[Task]] = for {
  //      //        _ <- lock()
  //        response <- getWork()
  //        res <- process(response)
  //        submitRes <- submitResults(res)
  //      } yield submitRes
  //      //      f.onComplete {
  //      //        case _ => unlock()
  //      //      }
  //      // FIXME pass results out
  //      f.map(t => ())
  //    }
  //  }

  override def status: ConversionStatus = currentStatus

  /**
    * Request work from Queue.
    */
  def getWork(): Future[Try[Job]] = {
    //    lock()
    val request: WSRequest = ws.url(requestUrl)
    logger.debug(s"Get work ${request.uri}")
    val complexRequest: WSRequest = request
      .withHeaders(
        "Accept" -> "application/json",
        AUTH_TOKEN_HEADER -> authToken.get)
      .withRequestTimeout(10000.millis)
    complexRequest.post(Json.toJson(transtypes))
      .flatMap { response =>
        response.status match {
          case OK => response.json.validate[Job]
            .map {
              case job => Future(Success(job))
            }
            .recoverTotal {
              e => Future(Failure(new IllegalArgumentException("Invalid queue response:" + JsError.toJson(e))))
            }
          case NO_CONTENT => idle()
          case UNAUTHORIZED => Future(Failure(new IllegalStateException(s"Unauthorized, login: ${response.status}")))
          case _ => Future(Failure(new IllegalArgumentException(s"Got unexpected response from queue: ${response.status}")))
        }
      }
  }

  def submitResults(res: Try[Task]): Future[Try[Task]] = {
    logger.debug("Submit: " + res)
    val f = res match {
      case f@Failure(NoWorkException()) => Future(f)
      case _ => {
        val job = res match {
          case Success(task) => task.job.copy(status = StatusString.Done)
          case Failure(ProcessorException(_, job)) => job.copy(status = StatusString.Error)
          case _@t => throw new IllegalArgumentException(t.toString)
        }
        // FIXME
        val log = List() //cacheListener.messages.map(_.msg.toString)
        //        cacheListener.messages.clear()
        val otRes: JobResult = JobResult(job, log)

        val request: WSRequest = ws.url(submitUrl)
        val complexRequest: WSRequest = request
          .withHeaders(
            AUTH_TOKEN_HEADER -> authToken.get)
          .withRequestTimeout(10000.millis)
        logger.debug(s"Submit ${otRes.job.id} results to queue")
        complexRequest
          .put(Json.toJson(otRes))
          .map { response =>
            response.status match {
              case OK => res
              case UNAUTHORIZED => Failure(new IllegalStateException(s"Unauthorized, login: ${response.status}"))
              case _ => Failure(new IllegalArgumentException(s"Submit responded with ${response.status}"))
            }
          }
      }
    }
    //    f.onComplete {
    //      case _ => unlock()
    //    }
    f
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

case class NoWorkException() extends Throwable
