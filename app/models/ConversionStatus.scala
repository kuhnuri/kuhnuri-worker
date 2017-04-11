package models

sealed trait ConversionStatus

case class Busy() extends ConversionStatus

case class Free() extends ConversionStatus
