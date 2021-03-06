package services

import models.{Task, Work}

import scala.concurrent.Future
import scala.util.Try

/**
  * Persists worker state to allow restarting after a crash.
  */
trait StateService {
  /**
    * Get persisted state.
    */
  def get(): Future[Option[Task]]

  /**
    * Clear persisted state.
    */
  def cleanJob(submitRes: Try[Work]): Future[Try[Work]]

  /**
    * Persist state.
    */
  def persist(src: Try[Task]): Future[Try[Task]]
}


