package services

import java.io.File
import java.net.URI
import java.nio.file.{Files, Paths}

import com.amazonaws.util.IOUtils
import javax.inject.Inject
import models.{Task, Work}
import play.Environment
import play.api.libs.ws.WSClient
import play.api.{Configuration, Logger}

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
        tidy <- cleanUp(res)
      } yield res
    }
    case Failure(e) => Future(Failure(e))
  }

  private def getProcessOutputDir(task: Task): URI =
    task.input
      .map(input => URI.create(input + ".zip"))
      .get

  /**
    * Download input resource into temporary location
    */
  def download(task: Task): Future[Try[Work]] = {
    def downloadResource(input: URI): Try[Work] = {
      val tempDir = baseTemp.toPath.resolve(s"${task.id}_${System.currentTimeMillis()}")
      input.getScheme match {
        case "file" if !input.getPath.endsWith("/") => {
          logger.info(s"Read source directly from " + task.input)
          Success(Work(
            URI.create(task.input.get),
            getProcessOutputDir(task),
            task,
            tempDir
          ))
        }
        case "s3" => {
          Files.createDirectories(tempDir)
          s3.download(input, tempDir)
            .map(tempInputFile => {
              val tempOutputFile = Paths.get(tempDir.toString, "output", tempInputFile.getFileName.toString)
              if (!Files.exists(tempOutputFile.getParent)) {
                Files.createDirectories(tempOutputFile.getParent)
              }
              Work(
                tempInputFile.toUri,
                tempOutputFile.toUri,
                task,
                tempDir
              )
            })
        }
        case "jar" => {
          val (resource, path) = Utils.parse(input)
          downloadResource(resource)
            .map { work =>
              work.copy(
                input = URI.create("jar:" + work.input + "!" + path)
              )
            }
        }
        case scheme =>
          logger.warn(s"Input URI scheme ${scheme} not supported");
          Failure(new ProcessorException(new IllegalArgumentException(s"Input URI scheme ${scheme} not supported"), task))
      }
    }

    Future {
      logger.debug(s"Download: " + task)
      try {
        val input = URI.create(task.input.get)
        logger.info(s"Input URI ${input} scheme ${input.getScheme}")
        downloadResource(input)
      } catch {
        case e: Exception =>
          Failure(new ProcessorException(e, task))
      }
    }
  }

  def transform(jobTry: Try[Work]): Future[Try[Work]]

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
  def upload(tryTask: Try[Work]): Future[Try[Work]] = {
    def uploadResource(work: Work, output: URI): Try[Work] = {
      output.getScheme match {
        case "file" if !output.getPath.endsWith("/") => {
          logger.info(s"Already generated output to ${work.output} directory")
          //            if (output != work.output) {
          //              throw new IllegalArgumentException(s"Output directory ${work.output} should match ${output}")
          //            }
          Success(work)
        }
        case "s3" => {
          assert(work.output.getScheme == "file")
          s3.upload(Paths.get(work.output), output)
            .map(_ => work)
        }
        case "jar" => {
          val (resource, _) = Utils.parse(output)
          uploadResource(work, resource)
        }
        case _ =>
          throw new IllegalArgumentException(s"Upload target ${work.output} not supported")
      }
    }

    Future {
      logger.debug(s"Upload: " + tryTask)
      tryTask match {
        case Success(work) => try {
          work.task.output match {
            case Some(taskOutput) => {
              val output = URI.create(taskOutput)
              uploadResource(work, output)
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
  }

  /**
    * Clean temporary files
    */
  private def cleanUp(tryTask: Try[Work]): Future[Try[Work]] = {
    Future {
      logger.info("Clean up")
      try {
        tryTask match {
          case Success(work) => {
            Utils.delete(work.temp.toFile)
          }
          case Failure(_) => // FIXME we need to retain temp dir info in failure case
        }
      } catch {
        case e: Exception => logger.error(s"Failed to clean up: ${e.getMessage}", e)
      }
      tryTask
    }
  }

  override def log(offset: Int): Seq[String] = List.empty

  override def addStopHook(callback: () => Unit): Unit = {
    stopHook = Some(callback)
  }
}
