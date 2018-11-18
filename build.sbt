import Dependencies._
import Deploy._

lazy val s3Resolver = "S3 Snapshots" at "s3://public.maven.globalwebindex.net.s3-eu-west-1.amazonaws.com/snapshots"

crossScalaVersions in ThisBuild := Seq("2.12.6", "2.11.8")
organization in ThisBuild := "net.globalwebindex"
resolvers in ThisBuild += "Maven Central Google Mirror EU" at "https://maven-central-eu.storage-download.googleapis.com/repos/central/data/"
version in ThisBuild ~= (_.replace('+', '-'))
dynver in ThisBuild ~= (_.replace('+', '-'))
cancelable in ThisBuild := true

lazy val core = (project in file("core"))
  .settings(libraryDependencies ++= Seq(awsS3, commonsMath, loggingImplLogback % "provided", scalatest) ++ loggingApi ++ jackson.map(_ % "test"))
  .settings(publishSettings("GlobalWebIndex", "randagen", s3Resolver))

lazy val app = (project in file("app"))
  .enablePlugins(DockerPlugin, SmallerDockerPlugin, JavaAppPackaging)
  .settings(publish := { })
  .settings(libraryDependencies ++= clist ++ loggingApi ++ Seq(loggingImplLogback))
  .settings(Deploy.settings("gwiq", "randagen", "gwi.randagen.app.RanDaGenApp"))
  .dependsOn(core % "compile->compile;test->test")
