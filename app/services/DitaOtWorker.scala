package services

import java.io.File

import javax.inject.Inject
import models.{StatusString, Work}
import org.dita.dost.{Processor, ProcessorFactory}
import play.Environment
import play.api.Configuration
import play.api.libs.ws.WSClient
import services.Utils.format

import scala.collection.JavaConverters._
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

          val inputFile = getInputFile(work)
          val outputDir = work.temp.resolve("output").toFile

          logger.info(s"Running DITA-OT: " + work)
          val processor = getProcessor(work, inputFile, outputDir)
          processor.run()

          val outputFile = getOutputFile(work, outputDir)

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

  private def getInputFile(work: Work): File = work.input.getScheme match {
    case "jar" => {
      val (zip, path) = Utils.parse(work.input)
      val zipFile = new File(zip)
      val inputDir = work.temp.resolve("input").toFile
      Utils.unzip(zipFile, inputDir)
      new File(inputDir.toURI.resolve(path.getPath))
    }
    case "file" => new File(work.input)
    case _ => throw new IllegalArgumentException(s"Unsupported input URI scheme: ${work.input}")
  }

  private def getOutputFile(work: Work, outputDir: File): File = {
    val outputFile = work.temp.resolve(s"${work.task.id}.zip").toFile
    Utils.zipDir(outputDir, outputFile)
    outputFile
  }

  private def getProcessor(work: Work, inputFile: File, outputDir: File): Processor = {
    val processorFactory = ProcessorFactory.newInstance(ditaDir)
    val tempDir = work.temp.resolve("tmp").toFile
    processorFactory.setBaseTempDir(tempDir)

    val processor = processorFactory.newProcessor(work.task.transtype)
    //    logger.info(s"Message count: ${cacheListener.messages.size} -> ${cacheListener.messages}")
    //    assert(cacheListener.messages.isEmpty)
    cacheListener.messages.clear()
    processor.setLogger(cacheListener)
    processor.setProperties(work.task.params.asJava)
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
