package services

import java.net.URI
import java.nio.file.Path

import scala.util.Try

trait S3Client {

  def download(uri: URI, dir: Path): Try[Path]

  def upload(src: Path, uri: URI): Try[Unit]

}
