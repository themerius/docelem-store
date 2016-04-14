name := """docelem-store"""

version := "0.5.0-SNAPSHOT"

scalaVersion := "2.11.8"

resolvers += "SCAI Artifactory" at "http://scai-repos.scai.fraunhofer.de:8080/artifactory/libs-release-local/"
resolvers += "SCAI Artifactory Snapshot" at "http://scai-repos.scai.fraunhofer.de:8080/artifactory/libs-snapshot-local/"

resolvers += "JBoss Maven" at "https://repository.jboss.org/nexus/content/groups/public"

credentials += Credentials(Path.userHome / ".m2" / "sbt-credentials")

/* Example for ~/.m2/sbt-credentials
realm=Artifactory Realm
host=scai-repos.scai.fraunhofer.de
user=
password=
*/

updateOptions := updateOptions.value.withCachedResolution(true)

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.4.2",
  "com.typesafe.akka" %% "akka-testkit" % "2.4.2",
  "com.typesafe.akka" %% "akka-remote" % "2.4.2",
  "org.scala-lang.modules" %% "scala-xml" % "1.0.3",
  "org.apache.accumulo" % "accumulo-core" % "1.7.1",
  "org.apache.accumulo" % "accumulo-minicluster" % "1.7.1",
  "org.scalatest" %% "scalatest" % "2.2.4" % "test"
)

// Communication with broker
libraryDependencies += "org.apache.geronimo.specs" % "geronimo-jms_1.1_spec" % "1.1.1"
libraryDependencies += "org.fusesource.stompjms" % "stompjms-client" % "1.19"

// SCAI Artifacts
libraryDependencies += "de.fraunhofer.scai.bio.uima" % "UIMACorePipelet" % "7.0"
libraryDependencies += "de.fraunhofer.scai.bio.uima" % "UIMATypeSystem" % "7.0"

// Fixing loggers. Kick out all logger implementations and include a simple SLF4J.
// Multiple SLF4J (http://stackoverflow.com/questions/25208943)
// Configure simple SLF4J (http://stackoverflow.com/questions/14544991)
// and (http://www.slf4j.org/api/org/slf4j/impl/SimpleLogger.html)
libraryDependencies ~= { _.map(_.exclude("ch.qos.logback", "logback-classic")) }
libraryDependencies ~= { _.map(_.exclude("org.slf4j", "slf4j-log4j12")) }
libraryDependencies ~= { _.map(_.exclude("log4j", "log4j")) }
libraryDependencies += "org.slf4j" % "slf4j-simple" % "1.7.21"

lazy val root = (project in file(".")).
  enablePlugins(BuildInfoPlugin).
  settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "eu.themerius.docelemstore"
  )

fork in run := true

// Bring the system property to the forked Java VM
javaOptions in run += s"-Dconfig.file=${System.getProperty("config.file")}"
//javaOptions in run += "-Xmx4G"
//javaOptions in run += "-server"
//javaOptions in run += "-XX:NewRatio=1"
//javaOptions in run += "-XX:+UseParallelOldGC"
javaOptions in run += "-Dhawtdispatch.threads=4"
javaOptions in test += "-XX:+CMSClassUnloadingEnabled"

// Avoid assembly errors
assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case "reference.conf" => MergeStrategy.concat
  case x => MergeStrategy.last
}
