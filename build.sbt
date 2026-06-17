val scala3Version = "3.7.1"

val appName: String = "InfraMapper"

libraryDependencies ++= Seq(
  "com.github.scopt" %% "scopt" % "4.0.1+",
  "com.softwaremill.sttp.client4" %% "core" % "4.0.0-RC1",
  "com.lihaoyi" %% "os-lib" % "0.11.3",
  "org.scalatest" %% "scalatest" % "3.2.20" % Test,
  "com.github.pathikrit" %% "better-files" % "3.9.2"
)

val circeVersion = "0.14.13"

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser",
).map(_ % circeVersion)

lazy val root = project
  .in(file("."))
  .settings(
    name := appName,
    version := "0.2.0-SNAPSHOT",

    scalaVersion := scala3Version,
    libraryDependencies += "org.scalameta" %% "munit" % "1.0.0" % Test,
    assemblyJarName in assembly := s"$appName.jar",
  )