organization := "com.elovirta.kuhnuri"

name := """worker"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .aggregate(common)
  .dependsOn(common)

lazy val common = project

scalaVersion := "2.11.8"

libraryDependencies += ehcache
libraryDependencies += ws
libraryDependencies += filters
libraryDependencies += guice
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % "test"
libraryDependencies += "com.amazonaws" % "aws-java-sdk" % "1.9.6"

sources in(Compile, doc) := Seq.empty

publishArtifact in(Compile, packageDoc) := false

stage := {
  import java.nio.file._
  val log = streams.value.log

  val stageDir = stage.value
  val ditaOtVersion = System.getProperty("dita-ot.version", "3.1")
  log.info(s"DITA-OT version: $ditaOtVersion")

  val ditaOtDir = baseDirectory.value / "target" / "universal" / "tmp" / "dita-ot"
  val ignoreLibs = List("logback-classic", "logback-core") //, "commons-logging", "dost.jar", "dost-configuration.jar", "fop", "ant.jar", "ant-launcher.jar", "ant-apache-resolver", "xep", "xercesImpl", "xml-apis", "xml-resolver", "xmlgraphics-commons")
  if (Files.notExists(ditaOtDir.toPath)) {
    log.info("Downloading...")
    IO.unzipURL(new URL(s"https://github.com/dita-ot/dita-ot/releases/download/$ditaOtVersion/dita-ot-$ditaOtVersion.zip"), ditaOtDir)
    IO.copy(List("build_template.xml", "catalog-dita_template.xml", "integrator.xml")
      .map(name => (ditaOtDir / s"dita-ot-$ditaOtVersion" / name, stageDir / name))
    )
    List("bin", "lib", "plugins", "config", "xsl")
      .foreach { name => IO.copyDirectory(ditaOtDir / s"dita-ot-$ditaOtVersion" / name, stageDir / name) }
    // remove because Play already has conf/logback.xml
    IO.delete(stageDir / "config" / "logback.xml")
    // remove duplicate or unneccessary jars
    (stageDir / "lib").list
      .filter(_.endsWith(".jar"))
      .filter(file => ignoreLibs.exists(ignore => file.contains(ignore)))
      .foreach(file => IO.delete(stageDir / "lib" / file))
    s"chmod 755 ${stageDir / "bin" / "dita" getAbsolutePath}" !
  } else {
    log.debug("Path exists, no need to download.")
  }

  log.info("Installing plugins...")

  log.info("Run integrator " + stageDir)
//  val adaptee = new Integrator(stageDir)
//  adaptee.setLogger(new SbtLogger(log))
//  adaptee.execute()
  val ditaCmd = stageDir / "bin" /(if (System.getProperty("os.name").toLowerCase().contains("win")) "dita.bat" else "dita")
  s"${ditaCmd getAbsolutePath} --install -v" !
  //  IO.copy(List(
  //    (stageDir / "config" / "org.dita.dost.platform" / "plugin.properties", stageDir / "conf" / "org.dita.dost.platform" / "plugin.properties"),
  //    (stageDir / "config" / "configuration.properties", stageDir / "conf" / "configuration.properties"),
  //    (stageDir / "config" / "plugins.xml", stageDir / "conf" / "plugins.xml"),
  //    (stageDir / "config" / "messages.xml", stageDir / "conf" / "messages.xml")
  //  ))

  log.debug("Rewrite launch scripts")
//  val ditaLibs: Seq[String] = (ditaOtDir / s"dita-ot-$ditaOtVersion" / "lib").list
//    .filter(_.endsWith(".jar"))
//    .filter(file => !ignoreLibs.exists(ignore => file.contains(ignore)))

  val shellFile = stageDir / "bin" / "worker"
  val shellLines = IO.readLines(shellFile).flatMap(line => {
    if (line.contains("app_classpath=")) {
//      val classpath: String = ditaLibs.map(lib => "$lib_dir/" + lib).mkString(":")
      List(
        "declare -x -r DITA_HOME=\"$(realpath \"${app_home}/../\")\"",
        "unset CLASSPATH",
//        "declare -x CLASSPATH=\"" + classpath + "\"",
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
//      val classpath: String = ditaLibs.map(lib => "%APP_LIB_DIR%\\" + lib).mkString(";")
      List(
        "set \"DITA_HOME=%WORKER_HOME%\"",
//        "set \"CLASSPATH=" + classpath + "\"",
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
