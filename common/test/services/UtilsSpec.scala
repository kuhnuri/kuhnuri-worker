package services

import java.net.URI

import org.scalatest._

class UtilsSpec extends FlatSpec with Matchers {

  "parse" should "return file and path" in {
    val jarURI = URI.create("jar:s3://bucket/key/file.zip!/path/file.txt")
    val (file, path) = Utils.parse(jarURI)
    file shouldBe URI.create("s3://bucket/key/file.zip")
    path shouldBe URI.create("path/file.txt")
  }
  it should "throw exception for missing path" in {
    val jarURI = URI.create("jar:file:/foo/bar.zip")
    a [IllegalArgumentException] should be thrownBy {
      Utils.parse(jarURI)
    }
  }
  it should "throw exception for incorrect URI scheme" in {
    val jarURI = URI.create("file:/foo/bar.zip")
    a [IllegalArgumentException] should be thrownBy {
      Utils.parse(jarURI)
    }
  }
}
