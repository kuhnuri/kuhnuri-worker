package services

import models.{Task, Work}

import scala.concurrent.Future
import scala.util.Try

/**
  * Persists worker state to allow restarting after a crash.
  */
trait StateService {
  def get(): Future[Option[Task]]

  def cleanJob(submitRes: Try[Work]): Future[Try[Work]]

  def persist(src: Try[Task]): Future[Try[Task]]
}


