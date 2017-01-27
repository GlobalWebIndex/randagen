import gwi.sbt.CommonPlugin
import gwi.sbt.CommonPlugin.autoImport._

crossScalaVersions in ThisBuild := Seq("2.11.8", "2.12.1")
organization in ThisBuild := "net.globalwebindex"

lazy val randagen = (project in file("."))
  .aggregate(core, app)

lazy val core = (project in file("core"))
  .enablePlugins(CommonPlugin)
  .settings(name := "randagen")
  .settings(libraryDependencies ++= Seq(awsS3, commonsMath, loggingImplLogback % "provided") ++ loggingApi ++ testingDeps ++ jackson.map(_ % "test"))
  .settings(publishSettings("GlobalWebIndex", "randagen", s3Resolver))

lazy val app = (project in file("app"))
  .enablePlugins(CommonPlugin, DockerPlugin)
  .settings(name := "randagen-app")
  .dependsOn(core % "compile->compile;test->test")
  .settings(libraryDependencies ++= loggingApi ++ Seq(loggingImplLogback))
  .settings(assemblySettings("randagen", Some("gwi.randagen.app.RanDaGenApp")))
  .settings(deploySettings("java:8", "gwiq", "randagen", "gwi.randagen.app.RanDaGenApp"))
