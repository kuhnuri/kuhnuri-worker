package services

import javax.inject.Inject
import play.api.Configuration

class TranstypesConfImpl @Inject()(configuration: Configuration) extends TranstypeConf {

  override def get: Set[String] = {
    configuration
      .get[Seq[String]]("worker.transtypes")
      .toSet
  }

}
