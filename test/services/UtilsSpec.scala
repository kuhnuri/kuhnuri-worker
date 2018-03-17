package services

import java.net.URI

import org.scalatest._

class UtilsSpec extends FlatSpec with Matchers {

  "parse" should "return file and path" in {
    val jarURI = URI.create("jar:file:/foo/bar.zip!/baz/qux")
    val (file, path) = Utils.parse(jarURI)
    file should be (URI.create("file:/foo/bar.zip"))
    path should be (URI.create("baz/qux"))
  }
  it should "throw exception" in {
    val jarURI = URI.create("jar:file:/foo/bar.zip")
    a [IllegalArgumentException] should be thrownBy {
      Utils.parse(jarURI)
    }
  }
}
