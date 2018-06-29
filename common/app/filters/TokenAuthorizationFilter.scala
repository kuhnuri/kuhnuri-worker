package filters

import akka.stream.Materializer
import javax.inject._
import play.api.Logger
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TokenAuthorizationFilter @Inject()(
                                          implicit override val mat: Materializer,
                                          exec: ExecutionContext) extends Filter {

  import TokenAuthorizationFilter._

  private val logger = Logger(this.getClass)

  override def apply(nextFilter: RequestHeader => Future[Result])
                    (requestHeader: RequestHeader): Future[Result] = {
    (requestHeader.headers.get(AUTH_TOKEN_HEADER), authToken) match {
      case (Some(token), Some(at)) if token == at => nextFilter(requestHeader)
      case (Some(_), _) => logger.info("Unrecognized API token"); Future(Results.Unauthorized)
      case (None, _) => logger.info("Missing API token"); Future(Results.Unauthorized)
    }
  }
}

object TokenAuthorizationFilter {
  val AUTH_TOKEN_HEADER = "X-Auth-Token"
  var authToken: Option[String] = None
}