package services

import java.io._
import java.net.URI
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{FileVisitResult, Files, Path, SimpleFileVisitor}
import java.util.zip.{ZipEntry, ZipInputStream, ZipOutputStream}

import scala.collection.mutable

object Utils {

  def zipDir(dir: File, out: File): Unit = {
    zip(out, collectOutput(dir))
  }

  private class CollectFiles(base: Path) extends SimpleFileVisitor[Path] {
    val files = mutable.Buffer[(File, Path)]()

    override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
      if (attrs.isRegularFile) {
        files += new Tuple2(file.toFile, base.relativize(file))
      }
      FileVisitResult.CONTINUE
    }
  }

  private def collectOutput(dir: File): Iterable[(File, Path)] = {
    val startingDir = dir.toPath
    val collector = new CollectFiles(startingDir);
    Files.walkFileTree(startingDir, collector);
    collector.files.toList
  }

  private def zip(out: File, files: Iterable[(File, Path)]) = {
    val buffer = 2 * 1024
    val zip = new ZipOutputStream(new FileOutputStream(out))
    try {
      var data = new Array[Byte](buffer)
      files.foreach { e =>
        val (file, name) = e
        zip.putNextEntry(new ZipEntry(name.toString.replace(File.separatorChar, '/')))
        val in = new BufferedInputStream(new FileInputStream(file), buffer)
        var b = in.read(data, 0, buffer)
        while (b != -1) {
          zip.write(data, 0, b)
          b = in.read(data, 0, buffer)
        }
        in.close()
        zip.closeEntry()
      }
    } finally {
      zip.close()
    }
  }

  def unzip(inZip: File, outDir: File): Unit = {
    val buffer = 2 * 1024
    val zip = new ZipInputStream(new FileInputStream(inZip))
    try {
      var data = new Array[Byte](buffer)
      var entry: ZipEntry = zip.getNextEntry
      while (entry != null) {
        val outFile = new File(outDir, entry.getName.replace('/', File.separatorChar))
        if (entry.isDirectory) {
          if (!outFile.exists() && !outFile.mkdirs()) {
            throw new IOException(s"Unable to make directory ${outFile}")
          }
        } else {
          if (!outFile.getParentFile.exists() && !outFile.getParentFile.mkdirs()) {
            throw new IOException(s"Unable to make directory ${outFile.getParent}")
          }
          //          println((entry.getName, outFile))
          val out = new BufferedOutputStream(new FileOutputStream(outFile))
          try {
            var b = zip.read(data, 0, buffer)
            while (b != -1) {
              out.write(data, 0, b)
              b = zip.read(data, 0, buffer)
            }
          } finally {
            out.close()
          }
        }
        zip.closeEntry()
        entry = zip.getNextEntry
      }
    } finally {
      zip.close()
    }
  }

  private val jarUriScheme = "jar"
  private val jarUriSeparator = "!/"

  /**
    * Parse JAR URI scheme
    *
    * @param input JAR URI
    * @return tuple of resource URI and file path
    */
  def parse(input: URI): (URI, URI) = {


    if (input.getScheme != jarUriScheme) {
      throw new IllegalArgumentException(s"Incorrect JAR URI scheme: ${input}")
    }
    val ssp = input.getSchemeSpecificPart
    ssp.indexOf(jarUriSeparator) match {
      case i if i != -1 => (new URI(ssp.substring(0, i)), new URI(ssp.substring(i + 2)))
      case _ => throw new IllegalArgumentException(s"Invalid JAR URI: ${input}")
    }
  }

  /**
    * Create Jar URI
    */
  def createJarUri(resource: URI, path: URI): URI = {
    if (path.isAbsolute || path.getPath == null) {
      throw new IllegalArgumentException(s"Invalid path URI: ${path}")
    }
    if (!resource.isAbsolute) {
      throw new IllegalArgumentException(s"Invalid resource URI: ${resource}")
    }
    URI.create(s"${jarUriScheme}:${resource}${jarUriSeparator}${path}")
  }

  /**
    * Recursively delete file or directory
    */
  def delete(tempDir: File): Unit = {
    def deleteWalker(file: File): Unit = {
      if (file.isDirectory) {
        file.listFiles.foreach(deleteWalker(_))
      }
      file.delete()
    }

    deleteWalker(tempDir)
  }

  def format(l: Long): String = "" + (l / 1000) + "." + (l % 1000) + " ms"

}
