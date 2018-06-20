package services

import java.io.File
import java.net.URI
//import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.Paths

//import com.amazonaws.{AmazonClientException, AmazonServiceException}
//import com.amazonaws.regions.{Region, Regions}
//import com.amazonaws.services.s3.model.{GetObjectRequest, PutObjectRequest}
//import com.amazonaws.services.s3.{AmazonS3, AmazonS3Client}
import javax.inject.Inject
import models.{Task, Work}
import play.Environment
import play.api.libs.ws.WSClient
import play.api.{Configuration, Logger}
//import services.Utils.format

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

abstract class BaseWorker @Inject()(implicit context: ExecutionContext,
                                    ws: WSClient,
                                    s3: S3Client,
                                    configuration: Configuration,
                                    environment: Environment) extends Worker {

  protected val logger = Logger(this.getClass)

  protected val baseTemp = new File(configuration.get[String]("worker.temp"))

  var stopHook: Option[() => Unit] = None

  override def process(tryJob: Try[Task]): Future[Try[Work]] = tryJob match {
    case Success(job) => {
      logger.info(s"Got job ${job.id}")
      for {
        src <- download(job)
        otRes <- transform(src)
        res <- upload(otRes)
      } yield res
    }
    case Failure(e) => Future(Failure(e))
  }

  private def getProcessOutputDir(task: Task): URI = task.input.map(input => URI.create(input + ".zip")).get

  /**
    * Download input resource into temporary location
    */
  def download(task: Task): Future[Try[Work]] = Future {
    logger.debug(s"Download: " + task)
    try {
      val input = URI.create(task.input.get)
      logger.info(s"Input URI ${input} scheme ${input.getScheme}")
      input.getScheme match {
        case "file" if !input.getPath.endsWith("/") => {
          logger.info(s"Read source directly from " + task.input)
          Success(Work(URI.create(task.input.get), getProcessOutputDir(task), task))
        }
        case "s3" => {
          val tempDir = Paths.get(baseTemp.getAbsolutePath, s"${task.id}_${System.currentTimeMillis()}")
          s3.download(input, tempDir)
            .map(tempInputFile => {
              val tempOutputFile = Paths.get(tempDir.toString, "output", tempInputFile.getFileName.toString)
              Work(
                tempInputFile.toUri,
                tempOutputFile.toUri,
                task)
            })
        }
        case scheme =>
          logger.warn(s"Input URI scheme ${scheme} not supported");
          Failure(new ProcessorException(new IllegalArgumentException(s"Input URI scheme ${scheme} not supported"), task))
      }
    } catch {
      case e: Exception =>
        Failure(new ProcessorException(e, task))
    }
  }

  def transform(jobTry: Try[Work]): Future[Try[Work]]

//  private def runDitaOt(jobTry: Try[Work]): Future[Try[Work]] = Future {
//    logger.debug(s"Process: " + jobTry)
//    jobTry match {
//      case Success(work) => {
//        try {
//          logger.info(s"Running graphics conversion: " + work)
//          val start = System.currentTimeMillis()
//
//          val file = new File(work.input)
//          logger.info(s"Open r+w ${work.input}")
//          logger.info("For each entry")
//          logger.info("  If entry is PDF")
//          logger.info("    Convert entry to PNG")
//          logger.info("    Save PNG to ZIP")
//          logger.info("Close ZIP")
//
//          val end = System.currentTimeMillis()
//          logger.info(s"Process took ${format(end - start)}")
//
//          // FIXME
//          val output = URI.create("s3://" + "xdoccont-data-prod" + "/" + "wnc/temp/" + file.getName)
//
//          val res = work.copy(
//            task = work.task.copy(status = StatusString.Done),
//            output = output
//          )
//          Success(res)
//        } catch {
//          case e: Throwable if getError(e).isDefined => {
//            logger.debug("Error in runDitaOt: " + e.getMessage, e)
//            stopHook.foreach(callback => callback())
//            throw getError(e).get
//          }
//          case e: Exception => {
//            val res = work.task.copy(status = StatusString.Error)
//            Failure(new ProcessorException(e, res))
//          }
//        }
//      }
//      case f => f
//    }
//  }

  protected def getError(e: Throwable): Option[java.lang.Error] = {
    if (e.isInstanceOf[java.lang.Error]) {
      return Some(e.asInstanceOf[java.lang.Error])
    }
    if (e.getCause != null) {
      return getError(e.getCause)
    }
    None
  }

  /**
    * Upload temporary resource into output
    */
  def upload(tryTask: Try[Work]): Future[Try[Work]] = Future {
    logger.debug(s"Upload: " + tryTask)
    tryTask match {
      case Success(work) => try {
        val tempOutput = work.output
        work.task.output match {
          case Some(taskOutput) => {
            val output = URI.create(taskOutput)
            output.getScheme match {
              case "file" if !output.getPath.endsWith("/") => {
                logger.info(s"Already generated output to ${work.output} directory")
                //            if (output != work.output) {
                //              throw new IllegalArgumentException(s"Output directory ${work.output} should match ${output}")
                //            }
                Success(work)
              }
              case "s3" => {
                val tmp = new File(tempOutput).toPath
                s3.upload(tmp, output)
                  .map(_ => {
                    work
                  })
//                val (bucket, key) = S3Utils.parse(output)
//                try {
//                  logger.info(s"Upload ${tempOutput} to ${output}")
//                  val req = new PutObjectRequest(bucket, key, new File(tempOutput))
//                  s3.putObject(req)
//                  Success(work)
//                } catch {
//                  case ase: AmazonServiceException => {
//                    logger.error("Caught an AmazonServiceException, which means your request made it to Amazon S3, but was rejected with an error response for some reason.");
//                    logger.error("Error Message:    " + ase.getMessage());
//                    logger.error("HTTP Status Code: " + ase.getStatusCode());
//                    logger.error("AWS Error Code:   " + ase.getErrorCode());
//                    logger.error("Error Type:       " + ase.getErrorType());
//                    logger.error("Request ID:       " + ase.getRequestId());
//                    Failure(ase)
//                  }
//                  case ace: AmazonClientException => {
//                    logger.error("Caught an AmazonClientException, which means the client encountered a serious internal problem while trying to communicate with S3, such as not being able to access the network.");
//                    logger.error("Error Message: " + ace.getMessage())
//                    Failure(ace)
//                  }
//                }
              }
              case _ =>
                throw new IllegalArgumentException(s"Upload target ${work.output} not supported")
            }
          }
          case None => {
            logger.debug("Task has no output")
            Success(work)
          }
        }
      } catch {
        case e: Exception =>
          Failure(new ProcessorException(e, work.task))
      }
      case f => f
    }
  }

  override def log(offset: Int): Seq[String] = List.empty

  override def addStopHook(callback: () => Unit): Unit = {
    stopHook = Some(callback)
  }
}
