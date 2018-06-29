package services

import java.io.{File, IOException}
import java.net.URI

import org.scalatest._

class UtilsSpec extends FlatSpec with Matchers with BeforeAndAfter {

  private val tempDir = File.createTempFile("temp", System.nanoTime.toString)

  before {
    if (tempDir.exists() && !tempDir.delete()) {
      throw new IOException()
    }
    if (!tempDir.mkdirs()) {
      throw new IOException()
    }
  }

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

  "create JAR URI" should "construct valid JAR URI" in {
    Utils.createJarUri(URI.create("s3://bucket/key"), URI.create("entry")) shouldBe URI.create("jar:s3://bucket/key!/entry")
  }
  it should "throw exception for relative resource URI" in {
    a [IllegalArgumentException] should be thrownBy {
      Utils.createJarUri(URI.create("/bucket/key"), URI.create("entry"))
    }
  }
  it should "throw exception for absolute path URI" in {
    a [IllegalArgumentException] should be thrownBy {
      Utils.createJarUri(URI.create("s3://bucket/key"), URI.create("s3://bucket/entry"))
    }
  }

  it should "throw exception for null path in path URI" in {
    a [IllegalArgumentException] should be thrownBy {
      Utils.createJarUri(URI.create("s3://bucket/key"), URI.create("s3://bucket"))
    }
  }

  "unzip" should "extract all files" in {
    val zip = new File(getClass.getResource("/test.zip").toURI)
    Utils.unzip(zip, tempDir)

    new File(tempDir, "file.txt").exists shouldBe true
    new File(tempDir, "file.txt").isFile shouldBe true
    new File(tempDir, "dir").exists() shouldBe true
    new File(tempDir, "dir").isDirectory shouldBe true
    new File(tempDir, "dir" + File.separator + "file.txt").exists shouldBe true
    new File(tempDir, "dir" + File.separator + "file.txt").isFile shouldBe true
  }

  "format" should "render duration" in {
    Utils.format(1) shouldBe "0.1 ms"
    Utils.format(1000) shouldBe "1.0 ms"
    Utils.format(1000000) shouldBe "1000.0 ms"
  }

  after {
    Utils.delete(tempDir)
  }
}
