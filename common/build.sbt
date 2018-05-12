
name := """com.elovirta.kuhnuri.worker.common"""

version := "1.0-SNAPSHOT"

lazy val common = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.8"

//resolvers += Resolver.mavenLocal

libraryDependencies += ehcache
libraryDependencies += ws
libraryDependencies += filters
libraryDependencies += guice
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % "test"
//libraryDependencies += "org.apache.ant" % "ant" % "1.10.2"
//libraryDependencies += "org.dita-ot" % "dost" % "3.0.3"

sources in (Compile, doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false
