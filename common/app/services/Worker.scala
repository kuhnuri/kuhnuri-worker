package services

import models.{Task, Work}

import scala.concurrent.Future
import scala.util.Try

/**
  * Performs a tranformation job.
  */
trait Worker {
  def log(offset: Int): Seq[String]

  def process(tryJob: Try[Task]): Future[Try[Work]]

  def addStopHook(callback: () => Unit): Unit
}


case class ProcessorException(e: Throwable, job: Task) extends Exception(e)
