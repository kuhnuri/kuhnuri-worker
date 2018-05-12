package models

import java.net.URI

import models.Work.uriWrites
import play.api.libs.functional.syntax._
import play.api.libs.json._

case class Register(id: String, password: String, uri: URI)

object Register {

//  def parse(id: String, uri: String) = Register(id, new URI(uri))

  implicit val registerWrites: Writes[Register] = (
    (__ \ "id").write[String] and
      (__ \ "password").write[String] and
      (__ \ "uri").write[URI]
  )(unlift(Register.unapply _))

}
