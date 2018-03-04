package services

import java.io.File
import java.net.URI

import javax.inject.Inject
import models._
import org.dita.dost.{Processor, ProcessorFactory}
import play.Environment
import play.api.{Configuration, Logger}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
  * Performs a tranformation job.
  */
trait Worker {
  //  def add(job: String): Unit

  //  def run(id: String): Option[JobStatus]

  //  def cancel: Status

  def log(offset: Int): Seq[String]

  def process(tryJob: Try[Job]): Future[Try[Task]]

  //  def run(): Future[Unit]

  //  def shutdown(): Unit
}

class SimpleWorker @Inject()(implicit context: ExecutionContext,
                             configuration: Configuration, environment: Environment) extends Worker {

  private val logger = Logger(this.getClass)

  private val ditaDir = environment.rootPath()
  private val processorFactory = ProcessorFactory.newInstance(ditaDir)
  private val baseTemp = new File(configuration.get[String]("worker.temp"))
  processorFactory.setBaseTempDir(baseTemp)

  protected val cacheListener = new CacheListener()
  //  protected var shutdownPromise = false

  //  override def shutdown(): Unit = {
  //    shutdownPromise = true
  //  }

  private def download(job: Job): Future[Try[Task]] = Future {
    logger.debug(s"Download: " + job)
    try {
      val input = new URI(job.input)
      logger.info(s"Input URI ${input} scheme ${input.getScheme}")
      input.getScheme match {
        case "file" | "http" | "https" if !input.getPath.endsWith("/") => {
          logger.info(s"Read source directly from " + job.input)
          Success(Task(new URI(job.input), getProcessOutputDir(job), job))
        }
        case "jar" => {
          val (srcUri, fileUri) = parse(input)
          val tmpIn = new File(srcUri)
          val tmpOut = new File(baseTemp, job.id + File.separator + "input")
          srcUri.getScheme match {
            case "file" if !srcUri.getPath.endsWith("/") => {
              logger.info(s"Unzip ${srcUri} ZIP to ${tmpOut.toURI}")
              Utils.unzip(tmpIn, tmpOut)
              val inputUri = tmpOut.toURI.resolve(fileUri)
              logger.info(s"Use processing input " + inputUri)
              Success(Task(inputUri, getProcessOutputDir(job), job))
            }
            //            case "http" | "https" => {
            //              // Download file
            //              // Unzip
            //            }
            case _ => throw new IllegalArgumentException(s"Download target ${srcUri} not supported")
          }
        }
        case scheme => logger.warn(s"Input URI scheme ${scheme} not supported"); Failure(new ProcessorException(new IllegalArgumentException(s"Input URI scheme ${scheme} not supported"), job))
      }
    } catch {
      case e: Throwable => e.printStackTrace(); Failure(new ProcessorException(e, job))
    }
  }

  private def getProcessOutputDir(job: Job): URI = {
    val out = new URI(job.output)
    out.getScheme match {
      case "file" if out.getPath.endsWith("/") => {
        out
      }
      case _ => {
        new File(baseTemp, job.id + File.separator + "output").toURI
      }
    }
  }

  private def parse(input: URI): (URI, URI) = {
    val ssp = input.getSchemeSpecificPart
    ssp.indexOf('!') match {
      case i if i != -1 => (new URI(ssp.substring(0, i)), new URI(ssp.substring(i + 2)))
      case _ => throw new IllegalArgumentException(s"Invalid JAR URI ${input}")
    }

  }

  private def format(l: Long): String = "" + (l / 1000) + "." + (l % 1000) + " ms"

  private def runDitaOt(jobTry: Try[Task]): Future[Try[Task]] = Future {
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
          val res = task.copy(job = task.job.copy(status = StatusString.Done))
          Success(res)
        } catch {
          case e: Throwable => {
            e.printStackTrace();
            val res = task.job.copy(status = StatusString.Error)
            Failure(new ProcessorException(e, res))
          }
        }
      }
      case f => f
    }
  }

  private def getProcessor(task: Task): Processor = {
    val processorFactory = ProcessorFactory.newInstance(ditaDir)
    val tempDir = new File(baseTemp, task.job.id + File.separator + "tmp")
    processorFactory.setBaseTempDir(tempDir)

    val processor = processorFactory.newProcessor(task.job.transtype)
    //    logger.info(s"Message count: ${cacheListener.messages.size} -> ${cacheListener.messages}")
    //    assert(cacheListener.messages.isEmpty)
    cacheListener.messages.clear()
    processor.setLogger(cacheListener)
    processor.setInput(task.input)
    processor.setOutputDir(new File(task.output))
    processor.setProperty("clean.temp", "false")
    processor.setProperty("dita.temp.dir", tempDir.getAbsolutePath)
    processor
  }

  private def upload(tryTask: Try[Task]): Future[Try[Task]] = Future {
    logger.debug(s"Upload: " + tryTask)
    tryTask match {
      case Success(task) => try {
        val output = new URI(task.job.output)
        output.getScheme match {
          case "file" if output.getPath.endsWith("/") => {
            logger.info(s"Already generated output to ${task.output} directory")
            if (output != task.output) {
              throw new IllegalArgumentException(s"Output directory ${task.output} should match ${output}")
            }
            Success(task)
          }
          case "jar" => {
            val (srcUri, _) = parse(output)
            srcUri.getScheme match {
              case "file" if !srcUri.getPath.endsWith("/") => {
                logger.info(s"ZIP generated output from ${task.output} to ${srcUri}")
                Utils.zipDir(new File(task.output), new File(srcUri))
                Success(task)
              }
              //              case "http" | "https" => {
              //                // ZIP output directory
              //                // Upload ZIP
              //              }
              case _ => throw new IllegalArgumentException(s"Upload target ${srcUri} not supported")
            }
          }
          case _ => throw new IllegalArgumentException(s"Upload target ${output} not supported")
        }
      } catch {
        case e: Throwable => e.printStackTrace(); Failure(new ProcessorException(e, task.job))
      }
      case f => f
    }
  }

  override def process(tryJob: Try[Job]): Future[Try[Task]] = tryJob match {
    case Success(job) => {
      logger.info(s"Got job ${job.id}")
      for {
        src <- download(job)
        otRes <- runDitaOt(src)
        res <- upload(otRes)
      } yield res
    }
    case Failure(e) => Future(Failure(e))
  }

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
}

case class ProcessorException(e: Throwable, job: Job) extends Exception(e)
