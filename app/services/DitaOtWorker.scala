package services

import java.io.File
import java.net.URI

import javax.inject.Inject
import models.{StatusString, Task, Work}
import org.dita.dost.{Processor, ProcessorFactory}
import play.Environment
import play.api.libs.ws.WSClient
import play.api.{Configuration, Logger}
import services.Utils.{format, parse}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class DitaOtWorker @Inject()(implicit context: ExecutionContext,
                             ws: WSClient,
                             s3: S3Client,
                             configuration: Configuration,
                             environment: Environment) extends BaseWorker() {

//  private val logger = Logger(this.getClass)

  private val ditaDir = environment.rootPath()
  private val processorFactory = ProcessorFactory.newInstance(ditaDir)
//  private val baseTemp = new File(configuration.get[String]("worker.temp"))
  processorFactory.setBaseTempDir(baseTemp)

  protected val cacheListener = new CacheListener()
//  var stopHook: Option[() => Unit] = None

//  private def download(job: Task): Future[Try[Work]] = Future {
//    logger.debug(s"Download: " + job)
//    try {
//      val input = new URI(job.input)
//      logger.info(s"Input URI ${input} scheme ${input.getScheme}")
//      input.getScheme match {
//        case "file" | "http" | "https" if !input.getPath.endsWith("/") => {
//          logger.info(s"Read source directly from " + job.input)
//          Success(Work(new URI(job.input), getProcessOutputDir(job), job))
//        }
//        case "jar" => {
//          val (srcUri, fileUri) = parse(input)
//          val tmpIn = new File(srcUri)
//          val tmpOut = new File(baseTemp, job.id + File.separator + "input")
//          srcUri.getScheme match {
//            case "file" if !srcUri.getPath.endsWith("/") => {
//              logger.info(s"Unzip ${srcUri} ZIP to ${tmpOut.toURI}")
//              Utils.unzip(tmpIn, tmpOut)
//              val inputUri = tmpOut.toURI.resolve(fileUri)
//              logger.info(s"Use processing input " + inputUri)
//              Success(Work(inputUri, getProcessOutputDir(job), job))
//            }
//            //            case "http" | "https" => {
//            //              // Download file
//            //              // Unzip
//            //            }
//            case _ =>
//              throw new IllegalArgumentException(s"Download target ${srcUri} not supported")
//          }
//        }
//        case scheme =>
//          logger.warn(s"Input URI scheme ${scheme} not supported");
//          Failure(new ProcessorException(new IllegalArgumentException(s"Input URI scheme ${scheme} not supported"), job))
//      }
//    } catch {
//      case e: Exception =>
////        e.printStackTrace();
//        Failure(new ProcessorException(e, job))
//    }
//  }

//  private def getProcessOutputDir(job: Task): URI = {
//    val out = new URI(job.output)
//    out.getScheme match {
//      case "file" if out.getPath.endsWith("/") => {
//        out
//      }
//      case _ => {
//        new File(baseTemp, job.id + File.separator + "output").toURI
//      }
//    }
//  }

  override def transform(jobTry: Try[Work]): Future[Try[Work]] = Future {
    logger.debug(s"Process: " + jobTry)
    jobTry match {
      case Success(task) => {
        try {
          logger.info(s"Running DITA-OT: " + task)
          val processor = getProcessor(task)
          val start = System.currentTimeMillis()
          processor.run()
          val end = System.currentTimeMillis()
          logger.info(s"Process took ${format(end - start)}")
          //          logger.debug(s"Stopped DITA-OT")
          val res = task.copy(task = task.task.copy(status = StatusString.Done))
          Success(res)
        } catch {
          case e: Throwable if getError(e).isDefined => {
            logger.debug("Error in runDitaOt: " + e.getMessage, e)
            stopHook.foreach(callback => callback())
            throw getError(e).get
          }
          case e: Exception => {
            val res = task.task.copy(status = StatusString.Error)
            Failure(new ProcessorException(e, res))
          }
        }
      }
      case f => f
    }
  }

//  private def getError(e: Throwable): Option[java.lang.Error] = {
//    if (e.isInstanceOf[java.lang.Error]) {
//      return Some(e.asInstanceOf[java.lang.Error])
//    }
//    if (e.getCause != null) {
//      return getError(e.getCause)
//    }
//    None
//  }

  private def getProcessor(task: Work): Processor = {
    val processorFactory = ProcessorFactory.newInstance(ditaDir)
    val tempDir = new File(baseTemp, task.task.id + File.separator + "tmp")
    processorFactory.setBaseTempDir(tempDir)

    val processor = processorFactory.newProcessor(task.task.transtype)
    //    logger.info(s"Message count: ${cacheListener.messages.size} -> ${cacheListener.messages}")
    //    assert(cacheListener.messages.isEmpty)
    cacheListener.messages.clear()
    processor.setLogger(cacheListener)
    processor.setInput(task.input.toFile)
    processor.setOutputDir(task.output.toFile)
    processor.setProperty("clean.temp", "false")
    processor.setProperty("dita.temp.dir", tempDir.getAbsolutePath)
    processor
  }

//  private def upload(tryTask: Try[Work]): Future[Try[Work]] = Future {
//    logger.debug(s"Upload: " + tryTask)
//    tryTask match {
//      case Success(task) => try {
//        val output = new URI(task.task.output)
//        output.getScheme match {
//          case "file" if output.getPath.endsWith("/") => {
//            logger.info(s"Already generated output to ${task.output} directory")
//            if (output != task.output) {
//              throw new IllegalArgumentException(s"Output directory ${task.output} should match ${output}")
//            }
//            Success(task)
//          }
//          case "jar" => {
//            val (srcUri, _) = parse(output)
//            srcUri.getScheme match {
//              case "file" if !srcUri.getPath.endsWith("/") => {
//                logger.info(s"ZIP generated output from ${task.output} to ${srcUri}")
//                Utils.zipDir(new File(task.output), new File(srcUri))
//                Success(task)
//              }
//              //              case "http" | "https" => {
//              //                // ZIP output directory
//              //                // Upload ZIP
//              //              }
//              case _ =>
//                throw new IllegalArgumentException(s"Upload target ${srcUri} not supported")
//            }
//          }
////          case "s3" if output.getPath().endsWith(".zip") => {
//            // TODO
//            // ZIP output directory
//            // upload ZIP to S3
////          }
//          case _ =>
//            throw new IllegalArgumentException(s"Upload target ${output} not supported")
//        }
//      } catch {
//        case e: Exception =>
//          //          e.printStackTrace();
//          Failure(new ProcessorException(e, task.task))
//      }
//      case f => f
//    }
//  }

//  override def process(tryJob: Try[Task]): Future[Try[Work]] = tryJob match {
//    case Success(job) => {
//      logger.info(s"Got job ${job.id}")
//      for {
//        src <- download(job)
//        otRes <- runDitaOt(src)
//        res <- upload(otRes)
//      } yield res
//    }
//    case Failure(e) => Future(Failure(e))
//  }

  //  override def run(): Future[Unit] = {
  //    if (shutdownPromise) {
  //      logger.debug(s"Shutdown requested, return immediately")
  //      Future(())
  //    } else {
  //      val f: Future[Try[Task]] = for {
  ////        _ <- lock()
  //        response <- getWork()
  //        res <- process(response)
  //        submitRes <- submitResults(res)
  //      } yield submitRes
  ////      f.onComplete {
  ////        case _ => unlock()
  ////      }
  //      // FIXME pass results out
  //      f.map(t => ())
  //    }
  //  }

  override def log(offset: Int) = cacheListener.messages.slice(offset, cacheListener.messages.size)
    .map(msg => msg.msg)

//  override def addStopHook(callback: () => Unit): Unit = {
//    stopHook = Some(callback)
//  }
}
