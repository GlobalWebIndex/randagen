import gwi.sbt.CommonPlugin
import gwi.sbt.CommonPlugin.autoImport._

lazy val s3SnapshotResolver = "S3 Snapshots" at "s3://public.maven.globalwebindex.net.s3-website-eu-west-1.amazonaws.com/snapshots"

crossScalaVersions in ThisBuild := Seq("2.11.8", "2.12.1")
organization in ThisBuild := "net.globalwebindex"
version in ThisBuild := "0.10-SNAPSHOT"
resolvers in ThisBuild += s3SnapshotResolver

lazy val randagen = (project in file("."))
  .aggregate(core, app)

lazy val core = (project in file("core"))
  .enablePlugins(CommonPlugin)
  .settings(name := "randagen")
  .settings(libraryDependencies ++= Seq(awsS3, commonsMath, loggingImplLogback % "provided") ++ loggingApi ++ testingDeps ++ jackson.map(_ % "test"))
  .settings(publishSettings("GlobalWebIndex", "randagen", s3SnapshotResolver))

lazy val app = (project in file("app"))
  .enablePlugins(CommonPlugin, DockerPlugin)
  .settings(name := "randagen-app")
  .dependsOn(core % "compile->compile;test->test")
  .settings(libraryDependencies ++= loggingApi ++ Seq(loggingImplLogback))
  .settings(assemblySettings("randagen", Some("gwi.randagen.app.RanDaGenApp")))
  .settings(deploySettings("java:8", "gwiq", "randagen", "gwi.randagen.app.RanDaGenApp"))
