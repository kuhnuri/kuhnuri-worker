package models

sealed case class Update(id: String, status: Option[StatusString])
