package models.request

import java.net.URI

import play.api.libs.functional.syntax._
import play.api.libs.json._

case class Register(id: String, password: String, uri: URI)

object Register {

  implicit val uriWrites: Writes[URI] = Writes { uri => JsString(uri.toString) }

  implicit val registerWrites: Writes[Register] = (
    (__ \ "id").write[String] and
      (__ \ "password").write[String] and
      (__ \ "uri").write[URI]
    ) (unlift(Register.unapply _))

}
