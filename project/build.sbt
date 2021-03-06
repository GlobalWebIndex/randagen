logLevel := Level.Warn

resolvers ++= Seq(
  "Maven Central Google Mirror EU" at "https://maven-central-eu.storage-download.googleapis.com/repos/central/data/"
)

addSbtPlugin("com.typesafe.sbt"     % "sbt-native-packager"   % "1.3.10")
addSbtPlugin("io.get-coursier"      % "sbt-coursier"          % "1.0.2")
addSbtPlugin("com.dwijnand"         % "sbt-dynver"            % "3.0.0")