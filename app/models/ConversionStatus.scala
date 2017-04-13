package models

import play.api.libs.json.{JsObject, JsString, Writes}

sealed trait ConversionStatus

case class Busy() extends ConversionStatus

case class Free() extends ConversionStatus

object ConversionStatus {
  implicit val jobStatusWrites: Writes[models.ConversionStatus] =
    Writes[models.ConversionStatus](s => JsObject(Map(
      "status" -> (s match {
        case Free() => JsString("free")
        case Busy() => JsString("busy")
      })
    )))
}