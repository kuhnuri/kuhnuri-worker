import com.google.inject.AbstractModule
import services._

class Module extends AbstractModule {

  override def configure() = {
    bind(classOf[Worker]).to(classOf[SimpleWorker])
    bind(classOf[Poller]).to(classOf[RestPoller])
    bind(classOf[WorkerService]).to(classOf[SimpleWorkerService]).asEagerSingleton()
  }

}
