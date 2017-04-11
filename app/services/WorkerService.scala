package services

import javax.inject.{Inject, Singleton}

import models.{ConversionStatus, Task}
import play.api.Logger
import play.api.inject.ApplicationLifecycle

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

trait WorkerService {
  //  def add(job: String): Unit

  //  def run(id: String): Option[JobStatus]

  //  def cancel: Status

  def status: ConversionStatus

  def log(offset: Int): Seq[String]
}

@Singleton
class SimpleWorkerService @Inject()(implicit context: ExecutionContext,
                                    worker: Worker, poller: Poller,
                                    appLifecycle: ApplicationLifecycle) extends WorkerService {

  private val logger = Logger(this.getClass)

  /** Promise of application shutdown. */
  var shutdownPromise: Option[Promise[Unit]] = None
  appLifecycle.addStopHook { () =>
    logger.info("Shutdown requested")
    val promise = Promise[Unit]()
    shutdownPromise = Some(promise)
    val f = Future.sequence(List(
      promise.future.map { v => logger.info(s"shutdown promise.future result $v"); v },
//      Future { worker.shutdown() },
      poller.unregister().map { v => logger.info(s"unregister result $v"); v }
    ))
    f
  }

  logger.info("Start processing loop")
  Await.ready(infiniteLoop(), Duration.Inf)

  /** Infinite loop that processes DITA-OT. */
  private def infiniteLoop(): Future[Unit] = {
    val nf = loopTask()
    nf.onComplete {
      case Failure(_: NoSuchElementException) => logger.info("nested infiniteLoop: NoSuchElementException"); cleanup()
      case _ => ()
    }
    val f = Future(nf).map(_ => ())
//    f.onComplete {
//      case Failure(_: NoSuchElementException) => logger.info("infiniteLoop: NoSuchElementException"); cleanup()
//      case _ => ()
//    }
    f
  }

  private def loopTask(): Future[Unit] = {
    val f = run()
      .filter((u: Unit) => {
        logger.debug(s"Shutdown = ${shutdownPromise.isEmpty}")
        shutdownPromise.isEmpty
      })
      .flatMap(_ => loopTask())
//    f.onComplete {
//      case Failure(t: NoSuchElementException) => logger.info("loopTask: NoSuchElementException")
//      case Failure(t) => t.printStackTrace(); logger.error(s"Failure in loopTask $t")
//      case _ => ()
//    }
    f
  }

  private def run(): Future[Unit] = {
    if (shutdownPromise.isDefined) {
      logger.info("Shutdown requested, return immediately")
      Future(())
    } else {
      val f: Future[Try[Task]] = for {
      //        _ <- lock()
        response <- poller.getWork()
        res <- worker.process(response)
        submitRes <- poller.submitResults(res)
      } yield submitRes
      f.onComplete {
        //        case Failure(t: NoSuchElementException) => ()
        case Failure(t) => t.printStackTrace(); logger.error(s"Failure in run: ${t.getMessage}", t)
        case _ => ()
        //        case _ => unlock()
      }
      // FIXME pass results out
      f.map(t => ())
    }
  }

  private def cleanup(): Unit = {
    logger.info("Worker cleanup")
    shutdownPromise.foreach(p => p.complete(Success(())))
  }

  override def status: ConversionStatus = poller.status

  override def log(offset: Int): Seq[String] = worker.log(offset)

}
