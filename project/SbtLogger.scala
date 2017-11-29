import org.dita.dost.log.DITAOTLogger
import org.slf4j.helpers.MarkerIgnoringBase
import sbt.Logger

class SbtLogger(log: Logger) extends MarkerIgnoringBase with DITAOTLogger {
  override def debug(msg: String): Unit = log.debug(msg)

  override def debug(msg: String, err: Throwable): Unit = log.debug(msg)

  override def debug(msg: String, args: Object*): Unit = log.debug(msg)

  override def debug(msg: String, arg1: Object, arg2: Object): Unit = log.debug(msg)

  override def debug(msg: String, arg: Object): Unit = log.debug(msg)

  override def error(msg: String): Unit = log.error(msg)

  override def error(msg: String, err: Throwable): Unit = {
    log.error(msg + ": " + err); err.printStackTrace()
  }

  override def error(msg: String, args: Object*): Unit = log.error(msg)

  override def error(msg: String, arg1: Object, arg2: Object): Unit = log.error(msg)

  override def error(msg: String, arg: Object): Unit = log.error(msg)

  override def getName(): String = null

  override def info(msg: String): Unit = log.info(msg)

  override def info(msg: String, err: Throwable): Unit = log.info(msg)

  override def info(msg: String, args: Object*): Unit = log.info(msg)

  override def info(msg: String, arg1: Object, arg2: Object): Unit = log.info(msg)

  override def info(msg: String, arg: Object): Unit = log.info(msg)

  override def isDebugEnabled(): Boolean = true

  override def isErrorEnabled(): Boolean = true

  override def isInfoEnabled(): Boolean = true

  override def isTraceEnabled(): Boolean = true

  override def isWarnEnabled(): Boolean = true

  override def trace(msg: String, err: Throwable): Unit = log.debug(msg)

  override def trace(msg: String, args: Object*): Unit = log.debug(msg)

  override def trace(msg: String, arg1: Object, arg2: Object): Unit = log.debug(msg)

  override def trace(msg: String, arg: Object): Unit = log.debug(msg)

  override def trace(msg: String): Unit = log.debug(msg)

  override def warn(msg: String): Unit = log.warn(msg)

  override def warn(msg: String, err: Throwable): Unit = log.warn(msg)

  override def warn(msg: String, arg1: Object, arg2: Object): Unit = log.warn(msg)

  override def warn(msg: String, args: Object*): Unit = log.warn(msg)

  override def warn(msg: String, arg: Object): Unit = log.warn(msg)
}