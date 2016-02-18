import _root_.sbtdocker.DockerPlugin.autoImport._
import _root_.sbtdocker.mutable.Dockerfile
import sbt.Keys._
import sbt._
import sbtassembly.AssemblyPlugin.autoImport._
import sbtdocker.DockerPlugin

object Build extends sbt.Build {

  val loggingDeps = Seq(
    "ch.qos.logback" % "logback-classic" % "1.1.3"
  )

  val testingDeps = Seq(
    "org.scalatest" %% "scalatest" % "3.0.0-M15" % "test"
  )

  val awsDeps = Seq(
    "com.amazonaws" % "aws-java-sdk-s3" % "1.10.26"
  )

  val publishSettings = Seq(
    publishMavenStyle := true,
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false },
    publishTo := {
      if (organization.value == "com.viagraphs")
        Some("snapshots" at "https://oss.sonatype.org/content/repositories/snapshots")
      else
        Some("S3 Snapshots" at "s3://maven.globalwebindex.net.s3-website-eu-west-1.amazonaws.com/snapshots")
    },
    pomExtra :=
      <url>https://github.com/GlobalWebIndex/randagen</url>
        <licenses>
          <license>
            <name>The MIT License (MIT)</name>
            <url>http://opensource.org/licenses/MIT</url>
            <distribution>repo</distribution>
          </license>
        </licenses>
        <scm>
          <url>git@github.com:GlobalWebIndex/randagen.git</url>
          <connection>scm:git:git@github.com:GlobalWebIndex/randagen.git</connection>
        </scm>
        <developers>
          <developer>
            <id>l15k4</id>
            <name>Jakub Liska</name>
            <email>jakub@globalwebindex.net</email>
          </developer>
        </developers>
  )

  val testSettings = Seq(
    testOptions in Test += Tests.Argument("-oD"),
    parallelExecution in Test := false,
    parallelExecution in ThisBuild := false,
    parallelExecution in IntegrationTest := false,
    testForkedParallel in ThisBuild := false,
    testForkedParallel in IntegrationTest := false,
    testForkedParallel in Test := false,
    concurrentRestrictions in Global += Tags.limit(Tags.Test, 1)
  )

  def deploySettings(repoName: String, appName: String, mainClassFqn: Option[String]) = {
    val workingDir = SettingKey[File]("working-dir", "Working directory path for running applications")
    Seq(
      /* FAT JAR */
      assembleArtifact := true,
      assemblyJarName in assembly := s"$appName.jar",
      workingDir := baseDirectory.value / "deploy",
      baseDirectory in run := workingDir.value,
      baseDirectory in runMain := workingDir.value,
      test in assembly := {},
      mainClass in assembly := mainClassFqn, // Note that sbt-assembly cannot assemble jar with multiple main classes use SBT instead
      aggregate in assembly := false,
      assemblyOutputPath in assembly := workingDir.value / "bin" / (assemblyJarName in assembly).value,

      /* DOCKER IMAGE */
      docker <<= (docker dependsOn assembly),
      dockerfile in docker :=
        new Dockerfile {
          from("java:8")
          run("/bin/mkdir", s"/opt/$appName")
          add(workingDir.value, s"/opt/$appName")
          workDir(s"/opt/$appName")
          entryPoint("java", "-jar", s"bin/$appName.jar")
        },
      imageNames in docker := Seq(
        ImageName(s"$repoName/$appName:${version.value}"),
        ImageName(s"$repoName/$appName:latest")
      )
    )
  }

  lazy val root = (project in file("."))
    .enablePlugins(DockerPlugin)
    .settings(
      organization := "net.globalwebindex",
      name := "randagen",
      scalaVersion := "2.11.7",
      version := "0.9-SNAPSHOT",
      scalacOptions ++= Seq(
        "-unchecked", "-deprecation", "-feature", "-Xfatal-warnings",
        "-Xlint", "-Xfuture",
        "-Yinline-warnings", "-Ywarn-adapted-args", "-Ywarn-inaccessible",
        "-Ywarn-nullary-override", "-Ywarn-nullary-unit", "-Yno-adapted-args"
      ),
      autoCompilerPlugins := true,
      cancelable in Global := true,
      libraryDependencies ++= awsDeps ++ loggingDeps ++ testingDeps ++ Seq(
        "org.apache.commons" % "commons-math3" % "3.6"
      )
    ).settings(testSettings ++ publishSettings)
    .settings(deploySettings("gwiq", "randagen", Some("gwi.randagen.RanDaGen")):_*)

}
