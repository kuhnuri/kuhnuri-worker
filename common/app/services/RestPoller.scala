package services

import java.net.{ConnectException, SocketException, URI, UnknownHostException}
import java.util.concurrent.TimeoutException

import filters.TokenAuthorizationFilter
import javax.inject.Inject
import models._
import models.request.{JobResult, Register}
import play.Environment
import play.api.http.Status.{NO_CONTENT, OK, UNAUTHORIZED}
import play.api.libs.json.{JsError, Json}
import play.api.libs.ws.{WSClient, WSRequest}
import play.api.{Configuration, Logger}

import scala.collection.immutable.List
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class RestPoller @Inject()(implicit context: ExecutionContext,
                           ws: WSClient,
                           configuration: Configuration,
                           environment: Environment,
                           transtypeConf: TranstypeConf)
  extends Poller {

  private val logger = Logger(this.getClass)

  private val queueBaseUrl = configuration.get[String]("queue.url")
  private val idleDuration = configuration.getMillis("worker.idle")
  private val registerUrl = s"${queueBaseUrl}api/v1/login"
  private val unregisterUrl = s"${queueBaseUrl}api/v1/logout"
  private val requestUrl = s"${queueBaseUrl}api/v1/work"
  private val submitUrl = s"${queueBaseUrl}api/v1/work"

  //  private val workerId      = configuration.get[String]("worker.id")
  private val queueUsername = configuration.get[String]("queue.username")
  private val queuePassword = configuration.get[String]("queue.password")
  private val workerBaseUrl = new URI(configuration.get[String]("worker.url"))

  protected val transtypes = transtypeConf.get
  protected var currentStatus: ConversionStatus = Busy()
  // FIXME What to do here, wait in loop until we can register?
  TokenAuthorizationFilter.authToken = Option.empty //register().toOption
  //  logger.debug(s"Token: ${TokenAuthorizationFilter.authToken}")

  private def getToken(): Future[Try[String]] = {
    if (TokenAuthorizationFilter.authToken.isDefined) {
      Future(Success(TokenAuthorizationFilter.authToken.get))
    } else {
      val register = Register(queueUsername, queuePassword, workerBaseUrl)
      logger.info(s"Register to queue $registerUrl as $register")
      ws.url(registerUrl)
        .withRequestTimeout(10000.millis)
        .post(Json.toJson(register))
        .map { response =>
          response.status match {
            case OK => {
              logger.info("Registration to queue done")
              response
                .header(TokenAuthorizationFilter.AUTH_TOKEN_HEADER)
                .map(token => {
                  TokenAuthorizationFilter.authToken = Option(token)
                  Success(token)
                })
                .getOrElse {
                  Failure(new UnauthorizedException("Token not available after registration"))
                }
            }
            // FIXME: don't throw, return Failure
            case UNAUTHORIZED =>
              TokenAuthorizationFilter.authToken = Option.empty
              logger.error(s"Unauthorized register")
              // XXX This should not throw an exception
              Failure(new UnauthorizedException(s"Unauthorized, login: ${response.status}"))
            case code =>
              logger.error(s"Unexpected queue response ${code}")
              Failure(new UnavailableException(s"Unsupported response $code", Option.empty))
          }
        }
        .recover {
          case e@(_: UnknownHostException | _: ConnectException | _: TimeoutException) =>
            logger.debug(s"Failed to reach queue at $registerUrl: ${e.getMessage}", e)
            Failure(new UnavailableException("Registration unavailable after timeout", Some(e)))
          case e: Exception =>
            // FIXME: don't throw, return Failure
            throw new IllegalStateException(s"Unable to register: ${e.getMessage}", e)
        }
    }
  }

  override def unregister(): Future[Unit] = {
    getToken().flatMap {
      case Success(token) => {
        logger.info(s"unregister: ${token}")
        ws.url(unregisterUrl)
          .addHttpHeaders(TokenAuthorizationFilter.AUTH_TOKEN_HEADER -> token)
          .withRequestTimeout(10000.millis)
          .post("")
          .map { response =>
            response.status match {
              case OK =>
                logger.info("Successfully unregistered")
                ()
              case UNAUTHORIZED =>
                TokenAuthorizationFilter.authToken = Option.empty
                logger.error(s"Unauthorized unregister")
                // XXX This should not throw exception
                throw new IllegalArgumentException(s"Unauthorized, login: ${response.status}")
              case code =>
                throw new IllegalArgumentException(
                  s"Unsupported response $code, login: ${response.status}"
                )
            }
          }
      }
      case Failure(e) => {
        logger.error(s"Failed to unregister: ${e.getMessage}", e)
        Future(Failure(e))
      }
    }
  }

  override def status: ConversionStatus = currentStatus

  /**
    * Request work from Queue.
    */
  def getWork(): Future[Try[Task]] = {
    getToken().flatMap {
      case Success(token) => {
        //    lock()
        val request: WSRequest = ws.url(requestUrl)
        logger.debug(s"Get work ${request.uri}")
        val complexRequest: WSRequest = request
          .addHttpHeaders("Accept" -> "application/json", TokenAuthorizationFilter.AUTH_TOKEN_HEADER -> token)
          .withRequestTimeout(10000.millis)
        val res: Future[Try[Task]] = complexRequest
          .post(Json.toJson(transtypes))
          .flatMap { response =>
            response.status match {
              case OK =>
                response.json
                  .validate[Task]
                  .map {
                    case task => Future(Success(task))
                  }
                  .recoverTotal { e =>
                    Future(
                      Failure(
                        new IllegalArgumentException(
                          s"Invalid queue response: ${JsError.toJson(e)}"
                        )
                      )
                    )
                  }
              case NO_CONTENT =>
                idle()
              case UNAUTHORIZED =>
                TokenAuthorizationFilter.authToken = Option.empty
                Future(
                  Failure(new UnauthorizedException(s"Unauthorized, login: ${response.status}"))
                )
              case _ =>
                Future(
                  Failure(
                    new IllegalArgumentException(
                      s"Got unexpected response from queue: ${response.status}"
                    )
                  )
                )
            }
          }
          .recover {
            case e@(_: UnknownHostException | _: ConnectException | _: TimeoutException | _: SocketException) =>
              Failure(new UnavailableException(s"Failed to submit: ${e.getMessage}", Some(e)))
            case e: Exception => {
              Failure(new Exception(s"Failed to request: ${e.getMessage}", e))
            }
          }
        res
      }
      case Failure(e) => {
        logger.error(s"Failed to get work: ${e.getMessage}", e)
        Future(Failure(e))
      }
    }
  }

  def submitResults(res: Try[Work]): Future[Try[Work]] = {
    val f = res match {
      case Success(task) =>
        submitJob(task.task.copy(status = StatusString.Done), res)
      case Failure(ProcessorException(_, task)) =>
        submitJob(task.copy(status = StatusString.Error), res)
      case f@Failure(NoWorkException()) =>
        Future(f)
      case f@Failure(UnauthorizedException(_)) =>
        Future(f)
      case f@Failure(UnavailableException(_, _)) =>
        Future(f)
      case _@Failure(t) =>
        throw new IllegalArgumentException("Unknown failure type: " + t.toString, t)
    }
    //    f.onComplete {
    //      case _ => unlock()
    //    }
    f
  }

  private def submitJob(task: Task, res: Try[Work]): Future[Try[Work]] = {
    getToken()
      .flatMap {
        case Success(token) => {
          logger.debug("Submit: " + res)
          // FIXME
          val log = List() //cacheListener.messages.map(_.msg.toString)
          //        cacheListener.messages.clear()
          val otRes: JobResult = JobResult(task, log)

          val request: WSRequest = ws.url(submitUrl)
          val complexRequest: WSRequest = request
            .addHttpHeaders(TokenAuthorizationFilter.AUTH_TOKEN_HEADER -> token)
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
              case e: Exception => {
                //                e.printStackTrace()
                Failure(new Exception("Failed to submit: " + e.getMessage, e))
              }
            }
        }
        case Failure(e) => {
          logger.error(s"Failed to submit job: ${e.getMessage}", e)
          Future(Failure(e))
        }
      }

  }

  private def idle(): Future[Try[Task]] = {
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
