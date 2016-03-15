name := """docelem-store"""

version := "0.3.0-SNAPSHOT"

scalaVersion := "2.11.8"

resolvers += "SCAI Artifactory" at "http://scai-repos.scai.fraunhofer.de:8080/artifactory/libs-release-local/"
resolvers += "SCAI Artifactory Snapshot" at "http://scai-repos.scai.fraunhofer.de:8080/artifactory/libs-snapshot-local/"

credentials += Credentials(Path.userHome / ".m2" / "sbt-credentials")

/* Example for ~/.m2/sbt-credentials
realm=Artifactory Realm
host=scai-repos.scai.fraunhofer.de
user=
password=
*/

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.11",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.11",
  "com.typesafe.akka" %% "akka-remote" % "2.3.11",
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

fork in run := true

// Bring the system property to the forked Java VM
javaOptions in run += s"-Dconfig.file=${System.getProperty("config.file")}"
//javaOptions in run += "-Xmx4G"
//javaOptions in run += "-server"
//javaOptions in run += "-XX:NewRatio=1"
//javaOptions in run += "-XX:+UseParallelOldGC"
javaOptions in run += "-Dhawtdispatch.threads=4"

// Avoid assembly errors
assemblyMergeStrategy in assembly := {
  case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
  case "reference.conf" => MergeStrategy.concat
  case x => MergeStrategy.last
}
