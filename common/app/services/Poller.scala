package services

import models._

import scala.concurrent.Future
import scala.util.Try

/**
  * Polls a Queue for jobs and submits them back after processing.
  */
trait Poller {

  def status: ConversionStatus

  def submitResults(res: Try[Work]): Future[Try[Work]]

  def getWork(): Future[Try[Task]]

  def unregister(): Future[Unit]

}



private case class NoWorkException() extends Exception()

private case class UnauthorizedException(msg: String) extends Exception(msg)

private case class UnavailableException(msg: String, cause: Option[Throwable])
    extends Exception(msg, cause.getOrElse(null))
