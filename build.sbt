import org.apache.tools.ant.BuildException
import org.dita.dost.log.DITAOTAntLogger
import org.dita.dost.log.DITAOTLogger
import org.dita.dost.platform.Integrator

name := """com.elovirta.kuhnuri.worker"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.8"

resolvers += Resolver.mavenLocal

libraryDependencies += cache
libraryDependencies += ws
libraryDependencies += filters
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1" % Test
libraryDependencies += "org.apache.ant" % "ant" % "1.9.7"
libraryDependencies += "org.dita-ot" % "dost" % "2.4.3"

stage := {
  import java.nio.file._
  import org.dita.dost.util.Constants
  val log = streams.value.log

  val stageDir = stage.value
  val ditaOtVersion = System.getProperty("dita-ot.version")
  log.info(s"DITA-OT version: $ditaOtVersion")

  val ditaOtDir = baseDirectory.value / "target" / "universal" / "tmp" / "dita-ot"
  if (Files.notExists(ditaOtDir.toPath)) {
    log.info("Downloading...")
    IO.unzipURL(new URL(s"https://github.com/dita-ot/dita-ot/releases/download/$ditaOtVersion/dita-ot-$ditaOtVersion.zip"), ditaOtDir)
    IO.copy(List("build_template.xml", "catalog-dita_template.xml", "integrator.xml")
      .map(name => (new File(new File(ditaOtDir, s"dita-ot-$ditaOtVersion"), name), new File(stageDir, name)))
    )
    List("lib", "plugins", "resources", "xsl")
     .foreach { name => IO.copyDirectory(new File(new File(ditaOtDir, s"dita-ot-$ditaOtVersion"), name), new File(stageDir, name)) }
  } else {
    log.debug("Path exists, no need to download.")
  }

  log.info("Installing plugins...")

  log.info("Run integrator")
  val adaptee = new Integrator(stageDir)
  adaptee.setLogger(new DITAOTLogger {
    override def info(msg: String): Unit = log.info(msg)
    override def warn(msg: String): Unit = log.warn(msg)
    override def error(msg: String): Unit = log.error(msg)
    override def error(msg: String, err: Throwable): Unit = {log.error(msg + ": " + err); err.printStackTrace()}
    override def debug(msg: String): Unit = log.debug(msg)
  })
  adaptee.execute()
  IO.copy(List(
    (stageDir / "lib" / "org.dita.dost.platform" / "plugin.properties", stageDir / "conf" / "org.dita.dost.platform" / "plugin.properties"),
    (stageDir / "lib" / "configuration.properties", stageDir / "conf" / "configuration.properties"),
    (stageDir / "resources" / "messages.xml", stageDir / "conf" / "messages.xml"),
    (stageDir / "resources" / "plugins.xml", stageDir / "conf" / "plugins.xml")
  ))

  log.debug("Rewrite launch scripts")
  val cmdFile = stageDir / "bin" / "com-elovirta-kuhnuri-worker"
  val cmdLines = IO.readLines(cmdFile).flatMap(line => {
    if (line.contains("app_classpath=")) {
      List(
        "declare -x -r DITA_HOME=\"$(realpath \"${app_home}/../\")\"",
        "unset CLASSPATH",
        "declare -x CLASSPATH",
        "source \"$lib_dir/../resources/env.sh\"",
        line.replace("app_classpath=\"", "app_classpath=\"$CLASSPATH:")
      )
    } else {
      List(line)
    }
  })
  IO.writeLines(cmdFile, cmdLines)

  stageDir
}

dist := {
  val stageDir = stage.value
  val distFile = baseDirectory.value / "target" / "universal" / s"${name.value.replace('.', '-')}-${version.value}.zip"
  IO.zip(Path.allSubpaths(stageDir), distFile)
  distFile
}
