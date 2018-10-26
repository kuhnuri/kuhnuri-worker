package services

import javax.inject.Inject
import models.{Task, Work}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

/**
  * Persist nothing.
  */
class NullStateService @Inject()(implicit context: ExecutionContext) extends StateService {

  override def get(): Future[Option[Task]] = Future { None }

  def cleanJob(submitRes: Try[Work]): Future[Try[Work]] = Future { submitRes }

  def persist(src: Try[Task]): Future[Try[Task]] = Future { src }

}
