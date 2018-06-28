organization := "com.elovirta.kuhnuri"

name := """worker-common"""

version := "1.0-SNAPSHOT"

lazy val common = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.8"

//resolvers += Resolver.mavenLocal

libraryDependencies += ehcache
libraryDependencies += ws
libraryDependencies += filters
libraryDependencies += guice
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % "test"
libraryDependencies += "com.amazonaws" % "aws-java-sdk" % "1.9.6"

sources in (Compile, doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false
