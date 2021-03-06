package services

import akka.actor.ActorSystem
import javax.inject.{Inject, Singleton}
import models.{ConversionStatus, StatusString, Task, Work}
import play.api.inject.ApplicationLifecycle
import play.api.{Configuration, Logger}
import org.slf4j.MDC

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

/**
  * Service that runs workers in an infinite loop.
  */
@Singleton
class SimpleWorkerService @Inject()(implicit context: ExecutionContext,
                                    configuration: Configuration,
                                    worker: Worker,
                                    poller: Poller,
                                    system: ActorSystem,
                                    stateService: StateService,
                                    appLifecycle: ApplicationLifecycle)
  extends WorkerService {

  private val logger = Logger(this.getClass)

  private val restart = configuration.get[Boolean]("worker.restart")

  /** Promise of application shutdown. */
  var shutdownPromise: Option[Promise[Unit]] = None
  appLifecycle.addStopHook { () =>
    logger.info("Shutdown requested")
    shutdownPromise
      .orElse(Some(Promise[Unit]()))
      .map { promise =>
        if (promise.isCompleted) {
          logger.info("Shutdown promise is already completed, return immediately")
          Future.successful(())
        } else {
          logger.info("Run shutdown process")
          Future.sequence(
            List(
              promise.future.map { v =>
                logger.info(s"shutdown promise.future result $v"); v
              },
              //      Future { worker.shutdown() },
              poller.unregister().map { v =>
                logger.info(s"unregister result $v"); v
              }
            )
          )
        }
      }
      .getOrElse(Future.successful(()))
  }
  worker.addStopHook { () =>
    val promise = Promise[Unit]()
    promise.success(())
    shutdownPromise = Some(promise)
  }

  logger.info("Start processing loop")
  Await.ready(infiniteLoop(), Duration.Inf)

  /** Infinite loop that processes DITA-OT. */
  private def infiniteLoop(): Future[Unit] = {
    val nf = loopTask()
    nf.onComplete {
      case Failure(_: NoSuchElementException) =>
        logger.info("nested infiniteLoop: NoSuchElementException");
        cleanup()
      case _ => ()
    }
    nf.failed.foreach {
      case e: java.lang.Error =>
        logger.error("infinite loop failure: " + e.getMessage, e)
      case e: Throwable =>
        logger.error("infinite loop throwable: " + e.getMessage, e)
    }
    val f = Future(nf).map(_ => ())
    //    f.onComplete {
    //      case Failure(_: NoSuchElementException) =>
    //        logger.info("infiniteLoop: NoSuchElementException");
    //        cleanup()
    //      case _ => ()
    //    }
    f
  }

  private def loopTask(): Future[Unit] = {
    //    logger.info("loopTask")
    val f = run()
      .filter((u: Unit) => {
        logger.debug(s"Shutdown = ${shutdownPromise.isDefined}")
        shutdownPromise.isEmpty
      })
      .flatMap(_ => loopTask())
    //    f.onComplete {
    //      case Failure(t: NoSuchElementException) => logger.info("loopTask: NoSuchElementException")
    //      case Failure(t) => t.printStackTrace(); logger.error(s"Failure in loopTask $t")
    //      case _ => ()
    //    }
    f.failed.foreach {
      case e: java.lang.Error =>
        logger.error("loopTask failure: " + e.getMessage, e)
      case e: Throwable =>
        logger.error("loopTask throwable: " + e.getMessage, e)
    }
    f
  }

  private def getWork(): Future[Try[Task]] = {
    stateService.get()
      .flatMap { oldTask: Option[Task] =>
        oldTask
          .map { t: Task =>
            logger.info("Using old job")
            if (restart) {
              Future(Success(t))
            } else {
              Future(Failure(new ProcessorException(new Exception("Unexpected worker shutdown"), t.copy(status = StatusString.Error))))
            }
          }
          .getOrElse(poller.getWork())
          .map { w: Try[Task] =>
            w.foreach { task =>
              MDC.put("id", task.id)
            }
            w
          }
      }
  }

  private def run(): Future[Unit] = {
    //    logger.info("run")
    if (shutdownPromise.isDefined) {
      logger.info("Shutdown requested, return immediately")
      Future(())
    } else {
      val f: Future[Try[Work]] = for {
        //        _ <- lock()
        response <- getWork()
        ser <- stateService.persist(response)
        res <- worker.process(ser)
        submitRes <- poller.submitResults(res)
        clean <- stateService.cleanJob(submitRes)
      } yield clean
      MDC.remove("id")
      f.foreach {
        case Failure(UnavailableException(msg, cause)) => {
          logger.debug("Queue unavailable, wait and retry: " + msg);
          Thread.sleep(5000)
          ()
        }
        case Failure(UnauthorizedException(msg)) => {
          logger.info("Unauthorized, wait and retry: " + msg);
          Thread.sleep(5000)
          ()
        }
        case Failure(NoWorkException()) => {
          logger.debug("No work");
          ()
        }
        case Failure(e) => {
          logger.error("Failure: " + e.getMessage, e);
          ()
        }
        case _ => ()
      }
      f.failed.foreach {
        case e: java.lang.Error => {
          //          logger.error("Got error and will re-throw: " + e.getMessage)
          //          e.printStackTrace()
          logger.error("Error in run: " + e.getMessage, e)
          throw e;
        }
        case t: Throwable => {
          //          t.printStackTrace()
          logger.error(s"Failure in run: ${t.getMessage}", t)
        }
        case _ => ()
      }
      // FIXME pass results out
      f.map(_ => ())
    }
  }

  private def cleanup(): Unit = {
    logger.info("Worker cleanup")
    shutdownPromise.foreach(p => p.complete(Success(())))
  }

  override def status: ConversionStatus = poller.status

  override def log(offset: Int): Seq[String] = worker.log(offset)

}
