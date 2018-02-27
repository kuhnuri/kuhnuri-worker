package services

import java.io.{File, IOException}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.{Files, Paths}

import javax.inject.Inject
import models.{Job, Task}
import play.api.libs.json.Json
import play.api.{Configuration, Logger}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
  * Persists worker state to allow restarting after a crash.
  */
trait StateService {
  def cleanJob(submitRes: Try[Task]): Future[Try[Task]]

  def persist(src: Try[Job]): Future[Try[Job]]
}

/**
  * Persists worker state to a JSON file on disk.
  */
class JsonStateService @Inject()(implicit context: ExecutionContext,
                                 configuration: Configuration) extends StateService {

  private val logger = Logger(this.getClass)

  private val baseTemp = new File(configuration.get[String]("worker.temp"))
  private val stateFile = baseTemp.toPath.resolve(Paths.get("state.json"))
  if (!Files.exists(stateFile.getParent)) {
    Files.createDirectories(stateFile.getParent);
  }

  def cleanJob(submitRes: Try[Task]): Future[Try[Task]] = Future {
    if (Files.exists(stateFile)) {
      logger.debug("Deleting " + stateFile)
      try {
        Files.delete(stateFile)
      } catch {
        case e: IOException => logger.error("Failed to delete persisted job state", e)
      }
    }
    submitRes
  }

  def persist(src: Try[Job]): Future[Try[Job]] = Future {
    src match {
      case Success(job) => {
        logger.debug("Writing " + stateFile)
        val out = Files.newBufferedWriter(stateFile, UTF_8, CREATE)
        try {
          out.write(Json.toJson(job).toString())
          src
        } catch {
          case e: IOException => {
            logger.error("Failed to persist job state", e)
            Failure(e)
          }
        } finally {
          out.close()
        }
      }
      case f => f
    }
  }

}
