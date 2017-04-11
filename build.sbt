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
