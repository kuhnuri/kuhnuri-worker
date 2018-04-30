package services

import models.{Task, Work}

import scala.concurrent.Future
import scala.util.Try

/**
  * Performs a tranformation job.
  */
trait Worker {
  //  def add(job: String): Unit

  //  def run(id: String): Option[JobStatus]

  //  def cancel: Status

  def log(offset: Int): Seq[String]

  def process(tryJob: Try[Task]): Future[Try[Work]]

  //  def run(): Future[Unit]

  def addStopHook(callback: () => Unit): Unit
}



case class ProcessorException(e: Throwable, job: Task) extends Exception(e)
