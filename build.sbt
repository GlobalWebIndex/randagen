import Dependencies._

crossScalaVersions in ThisBuild := Seq("2.13.3", "2.12.12")
organization in ThisBuild := "net.globalwebindex"
resolvers in ThisBuild += "Maven Central Google Mirror EU" at "https://maven-central-eu.storage-download.googleapis.com/repos/central/data/"
version in ThisBuild ~= (_.replace('+', '-'))
dynver in ThisBuild ~= (_.replace('+', '-'))
cancelable in ThisBuild := true
publishArtifact in ThisBuild := false
stage in (ThisBuild, Docker) := null

lazy val `randagen-core` = (project in file("core"))
  .settings(libraryDependencies ++= Seq(gcs, awsS3, commonsMath, loggingImplLogback % "provided", scalatest) ++ loggingApi ++ jackson.map(_ % "test"))
  .settings(Deploy.publishSettings("GlobalWebIndex", "randagen"))

lazy val `randagen-app` = (project in file("app"))
  .enablePlugins(DockerPlugin, JavaAppPackaging)
  .settings(libraryDependencies ++= clist ++ loggingApi ++ Seq(loggingImplLogback))
  .settings(Deploy.settings("gwiq", "randagen", "gwi.randagen.app.RanDaGenApp"))
  .dependsOn(`randagen-core` % "compile->compile;test->test")
