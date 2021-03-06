import com.typesafe.sbt.SbtNativePackager.autoImport._
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport._
import com.typesafe.sbt.packager.linux.LinuxPlugin.autoImport._
import sbt.Keys._
import sbt._

object Deploy {

  def settings(repository: String,
               appName: String,
               mainClassFqn: String): Seq[Def.Setting[_]] = {
    Seq(
      dockerUpdateLatest := false,
      dockerRepository := Some(repository),
      packageName in Docker := appName,
      dockerBaseImage in Docker := "anapsix/alpine-java:8u192b12_jdk_unlimited",
      mainClass in Compile := Some(mainClassFqn),
      defaultLinuxInstallLocation in Docker := s"/opt/$appName",
      dockerUpdateLatest in Docker := false
    )
  }

  def publishSettings(ghOrganizationName: String, ghProjectName: String) = Seq(
    publishArtifact := true,
    publishMavenStyle := true,
    publishArtifact in Test := false,
    publishTo := Some("GitHub Package Registry" at s"https://maven.pkg.github.com/$ghOrganizationName/$ghProjectName"),
    organization := "net.globalwebindex",
    credentials ++= sys.env.get("DMP_TEAM_GITHUB_TOKEN").map(Credentials("GitHub Package Registry", "maven.pkg.github.com", "dmp-team", _)),
    homepage := Some(url(s"https://github.com/$ghOrganizationName/$ghProjectName/blob/master/README.md")),
    licenses in ThisBuild += ("MIT", url("http://opensource.org/licenses/MIT")),
    developers += Developer("l15k4",
                            "Jakub Liska",
                            "liska.jakub@gmail.com",
                            url("https://github.com/l15k4")),
    scmInfo := Some(
      ScmInfo(url(s"https://github.com/$ghOrganizationName/$ghProjectName"),
              s"git@github.com:$ghOrganizationName/$ghProjectName.git")),
    pomIncludeRepository := { _ =>
      false
    },
    pomExtra :=
      <url>https://github.com/{ghOrganizationName}/{ghProjectName}</url>
        <licenses>
          <license>
            <name>The MIT License (MIT)</name>
            <url>http://opensource.org/licenses/MIT</url>
            <distribution>repo</distribution>
          </license>
        </licenses>
        <scm>
          <url>git@github.com:{ghOrganizationName}/{ghProjectName}.git</url>
          <connection>scm:git:git@github.com:{ghOrganizationName}/{ghProjectName}.git</connection>
        </scm>
        <developers>
          <developer>
            <id>l15k4</id>
            <name>Jakub Liska</name>
            <email>liska.jakub@gmail.com</email>
          </developer>
        </developers>
  )

}
