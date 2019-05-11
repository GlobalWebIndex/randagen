import sbt._

object Dependencies {

  val akkaVersion                       = "2.5.18"
  val jacksonVersion                    = "2.9.2"


  lazy val clist                        = Seq(
    "org.backuity.clist"            %%    "clist-core"                                % "3.5.0",
    "org.backuity.clist"            %%    "clist-macros"                              % "3.5.0"                 % "provided"
  )
  lazy val loggingApi                   = Seq(
    "org.slf4j"                     %     "slf4j-api"                                 % "1.7.25",
    "com.typesafe.scala-logging"    %%    "scala-logging"                             % "3.9.0"
  )

  lazy val jackson                      = Seq(
    "com.fasterxml.jackson.module"  %%    "jackson-module-scala"                  % jacksonVersion,
    "com.fasterxml.jackson.core"    %     "jackson-core"                          % jacksonVersion,
    "com.fasterxml.jackson.core"    %     "jackson-annotations"                   % jacksonVersion
  )

  lazy val loggingImplLogback           = "ch.qos.logback"                %     "logback-classic"                    % "1.2.3"

  lazy val commonsMath                  = "org.apache.commons"            %     "commons-math3"                      % "3.6.1"
  lazy val awsS3                        = "com.amazonaws"                 %     "aws-java-sdk-s3"                    % "1.11.413"
  lazy val gcs                          = "com.google.cloud"              %     "google-cloud-storage"               % "1.70.0"
  lazy val scalatest                    = "org.scalatest"                 %%    "scalatest"                          % "3.0.5"                 % "test"

}
