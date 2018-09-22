package services

import javax.inject.Inject
import play.api.Configuration

class TranstypesConfImpl @Inject()(configuration: Configuration) extends TranstypeConf {

  override def get: Set[String] = {
    configuration
      .getOptional[Seq[String]]("worker.transtypes").getOrElse(Seq.empty)
      .toSet
  }

}
