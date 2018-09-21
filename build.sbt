
version in ThisBuild := "0.1.6"
crossScalaVersions in ThisBuild := Seq("2.12.6", "2.11.8")
organization in ThisBuild := "net.globalwebindex"

lazy val javaDockerImage  = "anapsix/alpine-java:8u144b01_jdk_unlimited"

lazy val randagen = (project in file("."))
  .settings(aggregate in update := false)
  .settings(publish := {})
  .aggregate(`Randagen-core`, `Randagen-app`)

lazy val `Randagen-core` = (project in file("core"))
  .enablePlugins(CommonPlugin)
  .settings(libraryDependencies ++= Seq(awsS3, commonsMath, loggingImplLogback % "provided", scalatest) ++ loggingApi ++ jackson.map(_ % "test"))
  .settings(publishSettings("GlobalWebIndex", "randagen", s3Resolver))

lazy val `Randagen-app` = (project in file("app"))
  .enablePlugins(CommonPlugin, DockerPlugin)
  .settings(libraryDependencies ++= clist ++ loggingApi ++ Seq(loggingImplLogback))
  .settings(publish := { })
  .settings(deploy(DeployDef(config("app") extend Compile, javaDockerImage, "gwiq", "randagen", "gwi.randagen.app.RanDaGenApp")))
  .dependsOn(`Randagen-core` % "compile->compile;test->test")
