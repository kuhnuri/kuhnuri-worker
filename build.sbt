import org.dita.dost.platform.Integrator

organization := "com.elovirta.kuhnuri"

name := """worker"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .aggregate(common)
  .dependsOn(common)

lazy val common = project

scalaVersion := "2.11.8"

//resolvers += Resolver.mavenLocal

libraryDependencies += ehcache
libraryDependencies += ws
libraryDependencies += filters
libraryDependencies += guice
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % "test"
libraryDependencies += "org.apache.ant" % "ant" % "1.10.2"
libraryDependencies += "org.dita-ot" % "dost" % "3.0.3"
libraryDependencies += "com.amazonaws" % "aws-java-sdk" % "1.9.6"

sources in (Compile, doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false

stage := {
  import java.nio.file._
  val log = streams.value.log

  val stageDir = stage.value
  val ditaOtVersion = System.getProperty("dita-ot.version", "3.1")
  log.info(s"DITA-OT version: $ditaOtVersion")

  val ditaOtDir = baseDirectory.value / "target" / "universal" / "tmp" / "dita-ot"
  if (Files.notExists(ditaOtDir.toPath)) {
    log.info("Downloading...")
    IO.unzipURL(new URL(s"https://github.com/dita-ot/dita-ot/releases/download/$ditaOtVersion/dita-ot-$ditaOtVersion.zip"), ditaOtDir)
    IO.copy(List("build_template.xml", "catalog-dita_template.xml", "integrator.xml")
      .map(name => (new File(new File(ditaOtDir, s"dita-ot-$ditaOtVersion"), name), new File(stageDir, name)))
    )
    List("bin", "lib", "plugins", "config", "xsl")
      .foreach { name => IO.copyDirectory(new File(new File(ditaOtDir, s"dita-ot-$ditaOtVersion"), name), new File(stageDir, name)) }
  } else {
    log.debug("Path exists, no need to download.")
  }

  log.info("Installing plugins...")

  log.info("Run integrator " + stageDir)
  val adaptee = new Integrator(stageDir)
  adaptee.setLogger(new SbtLogger(log))
  adaptee.execute()
  IO.copy(List(
    (stageDir / "config" / "org.dita.dost.platform" / "plugin.properties", stageDir / "conf" / "org.dita.dost.platform" / "plugin.properties"),
    (stageDir / "config" / "configuration.properties", stageDir / "conf" / "configuration.properties"),
    (stageDir / "config" / "plugins.xml", stageDir / "conf" / "plugins.xml"),
    (stageDir / "config" / "messages.xml", stageDir / "conf" / "messages.xml")
  ))

  log.debug("Rewrite launch scripts")
  val shellFile = stageDir / "bin" / "worker"
  val shellLines = IO.readLines(shellFile).flatMap(line => {
    if (line.contains("app_classpath=")) {
      List(
        "declare -x -r DITA_HOME=\"$(realpath \"${app_home}/../\")\"",
        "unset CLASSPATH",
        "declare -x CLASSPATH",
        "source \"$lib_dir/../config/env.sh\"",
        line.replace("app_classpath=\"", "app_classpath=\"$CLASSPATH:$lib_dir/../config/:")
      )
    } else {
      List(line)
    }
  })
  IO.write(shellFile, shellLines.mkString("\n"))

  val cmdFile = stageDir / "bin" / "worker.bat"
  val cmdLines = IO.readLines(cmdFile).flatMap(line => {
    if (line.contains("APP_CLASSPATH=")) {
      List(
        "set \"DITA_HOME=%WORKER_HOME%\"",
        "set \"CLASSPATH=\"",
        "call \"%APP_LIB_DIR%\\..\\config\\env.bat\"",
        line.replace("APP_CLASSPATH=", "APP_CLASSPATH=%CLASSPATH%;%APP_LIB_DIR%\\..\\config\\;")
      )
    } else {
      List(line)
    }
  })
  IO.write(cmdFile, cmdLines.mkString("\r\n"))

  stageDir
}

dist := {
  val stageDir = stage.value
  val distFile = baseDirectory.value / "target" / "universal" / s"${name.value.replace('.', '-')}-${version.value}.zip"
  val entries: Traversable[(File, String)] = Path.allSubpaths(stageDir)
    .map((e) => (e._1, s"${name.value.replace('.', '-')}-${version.value}/${e._2}"))
  IO.zip(entries, distFile)
  distFile
}
