package models

@Deprecated
sealed case class Update(id: String, status: Option[StatusString])
