package services

import java.io.File
import java.nio.file.Paths

import javax.inject.Inject
import models.{StatusString, Work}
import org.dita.dost.{Processor, ProcessorFactory}
import play.Environment
import play.api.Configuration
import play.api.libs.ws.WSClient
import services.Utils.format

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class DitaOtWorker @Inject()(implicit context: ExecutionContext,
                             ws: WSClient,
                             s3: S3Client,
                             configuration: Configuration,
                             environment: Environment) extends BaseWorker() {

  private val ditaDir = environment.rootPath()
  private val processorFactory = ProcessorFactory.newInstance(ditaDir)
  processorFactory.setBaseTempDir(baseTemp)

  protected val cacheListener = new CacheListener()

  override def transform(jobTry: Try[Work]): Future[Try[Work]] = Future {
    logger.debug(s"Process: " + jobTry)
    jobTry match {
      case Success(work) => {
        try {
          val start = System.currentTimeMillis()

          val tempDir = new File(baseTemp, s"${work.task.id}_${System.currentTimeMillis()}")
          val inputFile = work.input.getScheme match {
            case "jar" => {
              val (zip, path) = Utils.parse(work.input)
              val zipFile = new File(zip)
              val inputDir = new File(tempDir, "input")
              Utils.unzip(zipFile, inputDir)
              new File(inputDir.toURI.resolve(path.getPath))
            }
            case "file" => new File(work.input)
            case _ => throw new IllegalArgumentException(s"Unsupported input URI scheme: ${work.input}")
          }
          val outputDir = new File(tempDir, "output")

          logger.info(s"Running DITA-OT: " + work)
          val processor = getProcessor(work, inputFile, outputDir)
          processor.run()

          val outputFile = new File(tempDir, work.task.id + ".zip")
          Utils.zipDir(outputDir, outputFile)

          val end = System.currentTimeMillis()
          logger.info(s"Process took ${format(end - start)}")

          val res = work.copy(
            output = outputFile.toURI,
            task = work.task.copy(
              status = StatusString.Done
            )
          )
          Success(res)
        } catch {
          case e: Throwable if getError(e).isDefined => {
            logger.debug("Error in runDitaOt: " + e.getMessage, e)
            stopHook.foreach(callback => callback())
            throw getError(e).get
          }
          case e: Exception => {
            val res = work.task.copy(status = StatusString.Error)
            Failure(new ProcessorException(e, res))
          }
        }
      }
      case f => f
    }
  }

  private def getProcessor(task: Work, inputFile: File, outputDir: File): Processor = {
    val processorFactory = ProcessorFactory.newInstance(ditaDir)
    val tempDir = new File(baseTemp, task.task.id + File.separator + "tmp")
    processorFactory.setBaseTempDir(tempDir)

    val processor = processorFactory.newProcessor(task.task.transtype)
    //    logger.info(s"Message count: ${cacheListener.messages.size} -> ${cacheListener.messages}")
    //    assert(cacheListener.messages.isEmpty)
    cacheListener.messages.clear()
    processor.setLogger(cacheListener)
    processor.setInput(inputFile)
    processor.setOutputDir(outputDir)
    processor.setProperty("clean.temp", "false")
    processor.setProperty("dita.temp.dir", tempDir.getAbsolutePath)
    processor
  }

  override def log(offset: Int) = cacheListener.messages
    .slice(offset, cacheListener.messages.size)
    .map(msg => msg.msg)

}
