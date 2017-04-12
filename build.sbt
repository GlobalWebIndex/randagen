import gwi.sbt.CommonPlugin
import gwi.sbt.CommonPlugin.autoImport._

crossScalaVersions in ThisBuild := Seq("2.12.1", "2.11.8")
organization in ThisBuild := "net.globalwebindex"

lazy val randagen = (project in file("."))
  .aggregate(`randagen-core`, `randagen-app`)

lazy val `randagen-core` = (project in file("core"))
  .enablePlugins(CommonPlugin)
  .settings(name := "randagen-core")
  .settings(libraryDependencies ++= Seq(awsS3, commonsMath, loggingImplLog4j % "provided") ++ loggingApi ++ testingDeps ++ jackson.map(_ % "test"))
  .settings(publishSettings("GlobalWebIndex", "randagen", s3Resolver))

lazy val `randagen-app` = (project in file("app"))
  .enablePlugins(CommonPlugin, DockerPlugin)
  .settings(name := "randagen-app")
  .settings(libraryDependencies ++= loggingApi ++ Seq(loggingImplLog4j))
  .settings(assemblySettings("randagen", Some("gwi.randagen.app.RanDaGenApp")))
  .settings(deploySettings("java:8", "gwiq", "randagen", "gwi.randagen.app.RanDaGenApp"))
  .dependsOn(`randagen-core` % "compile->compile;test->test")
