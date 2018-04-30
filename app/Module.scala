import com.google.inject.AbstractModule
import services._

class Module extends AbstractModule {

  override def configure() = {
    bind(classOf[Worker]).to(classOf[DitaOtWorker])
    bind(classOf[StateService]).to(classOf[JsonStateService])
    bind(classOf[Poller]).to(classOf[RestPoller]).asEagerSingleton()
    bind(classOf[WorkerService]).to(classOf[SimpleWorkerService]).asEagerSingleton()
  }

}
