package services

import org.slf4j.helpers.MarkerIgnoringBase

import scala.collection.mutable

class CacheListener extends MarkerIgnoringBase {

  val messages: mutable.Buffer[Msg] = mutable.Buffer[Msg]()

  override def warn(msg: String): Unit = messages += Warn(msg)

  override def warn(format: String, arg: scala.Any): Unit = throw new UnsupportedOperationException

  override def warn(format: String, arguments: AnyRef*): Unit = throw new UnsupportedOperationException

  override def warn(format: String, arg1: scala.Any, arg2: scala.Any): Unit = throw new UnsupportedOperationException

  override def warn(msg: String, t: Throwable): Unit = throw new UnsupportedOperationException

  override def isErrorEnabled: Boolean = throw new UnsupportedOperationException

  override def isInfoEnabled: Boolean = throw new UnsupportedOperationException

  override def isDebugEnabled: Boolean = throw new UnsupportedOperationException

  override def isTraceEnabled: Boolean = throw new UnsupportedOperationException

  override def error(msg: String): Unit = messages += Error(msg)

  override def error(format: String, arg: scala.Any): Unit = throw new UnsupportedOperationException

  override def error(format: String, arg1: scala.Any, arg2: scala.Any): Unit = throw new UnsupportedOperationException

  override def error(format: String, arguments: AnyRef*): Unit = throw new UnsupportedOperationException

  override def error(msg: String, t: Throwable): Unit = throw new UnsupportedOperationException

  override def debug(msg: String): Unit = () //messages += Debug(msg)

  override def debug(format: String, arg: scala.Any): Unit = throw new UnsupportedOperationException

  override def debug(format: String, arg1: scala.Any, arg2: scala.Any): Unit = throw new UnsupportedOperationException

  override def debug(format: String, arguments: AnyRef*): Unit = throw new UnsupportedOperationException

  override def debug(msg: String, t: Throwable): Unit = throw new UnsupportedOperationException

  override def isWarnEnabled: Boolean = throw new UnsupportedOperationException

  override def trace(msg: String): Unit = () // messages += Debug(msg)

  override def trace(format: String, arg: scala.Any): Unit = throw new UnsupportedOperationException

  override def trace(format: String, arg1: scala.Any, arg2: scala.Any): Unit = throw new UnsupportedOperationException

  override def trace(format: String, arguments: AnyRef*): Unit = throw new UnsupportedOperationException

  override def trace(msg: String, t: Throwable): Unit = throw new UnsupportedOperationException

  override def info(msg: String): Unit = () //messages += Info(msg)

  override def info(format: String, arg: scala.Any): Unit = throw new UnsupportedOperationException

  override def info(format: String, arg1: scala.Any, arg2: scala.Any): Unit = throw new UnsupportedOperationException

  override def info(format: String, arguments: AnyRef*): Unit = throw new UnsupportedOperationException

  override def info(msg: String, t: Throwable): Unit = throw new UnsupportedOperationException
}

sealed trait Msg {
  def msg: String
}

case class Debug(override val msg: String) extends Msg

case class Info(override val msg: String) extends Msg

case class Warn(override val msg: String) extends Msg

case class Error(override val msg: String) extends Msg