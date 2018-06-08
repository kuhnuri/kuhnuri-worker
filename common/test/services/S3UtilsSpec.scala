package services

import java.net.URI

import org.scalatest._

class S3UtilsSpec extends FlatSpec with Matchers {

  "parse" should "return file and path" in {
    val jarURI = URI.create("s3://bucket/key/file")
    val (file, path) = S3Utils.parse(jarURI)
    file should be ("bucket")
    path should be ("key/file")
  }
//  it should "throw exception" in {
//    val jarURI = URI.create("jar:file:/foo/bar.zip")
//    a [IllegalArgumentException] should be thrownBy {
//      Utils.parse(jarURI)
//    }
//  }
}
