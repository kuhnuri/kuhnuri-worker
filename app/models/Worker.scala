package models

import java.net.URI

import play.api.libs.functional.syntax._
import play.api.libs.json._

case class Register(uri: URI)

object Register {

  def parse(uri: String) = Register(new URI(uri))

  implicit val registerWrites: Writes[Register] =
    (__ \ "uri").write[String]
      .contramap { (register: Register) => register.uri.toString }

}
