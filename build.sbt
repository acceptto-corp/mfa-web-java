import sbt.Keys._

name := """acceptto-mfa-java"""

version := "1.0-SNAPSHOT"

scalaVersion := "2.10.4"

libraryDependencies ++= Seq(
  jdbc,
  javaEbean,
  cache,
  "org.mindrot" % "jbcrypt" % "0.3m",
  "com.typesafe" %% "play-plugins-mailer" % "2.2.0",
  filters,
  javaWs,
  "org.postgresql" % "postgresql" % "9.3-1100-jdbc4"
)

resolvers ++= Seq(
    "Apache" at "http://repo1.maven.org/maven2/",
    "jBCrypt Repository" at "http://repo1.maven.org/maven2/org/",
    "Sonatype OSS Snasphots" at "http://oss.sonatype.org/content/repositories/snapshots"
)

lazy val root = (project in file(".")).enablePlugins(play.PlayJava)

TwirlKeys.templateImports += "model._"
