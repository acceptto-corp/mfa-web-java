import sbt.Keys._

name := """acceptto-mfa-java"""

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
  jdbc,
  cache,
  "com.h2database" % "h2" % "1.4.192",
  "org.mindrot" % "jbcrypt" % "0.3m",
  filters,
  javaWs
)

dependencyOverrides ++= Set(
  "com.google.guava" % "guava" % "18.0",
  "org.jboss.logging" % "jboss-logging" % "3.2.1.Final",
  "io.netty" % "netty" % "3.10.6.Final"
)

resolvers ++= Seq(
    "Apache" at "http://repo1.maven.org/maven2/",
    "jBCrypt Repository" at "http://repo1.maven.org/maven2/org/",
    "Sonatype OSS Snasphots" at "http://oss.sonatype.org/content/repositories/snapshots"
)

lazy val root = (project in file(".")).enablePlugins(PlayJava, PlayEbean)
