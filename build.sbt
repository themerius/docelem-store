name := """docelem-store"""

version := "1.0"

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.11",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.11",
  "com.typesafe.akka" %% "akka-remote" % "2.3.11",
  "org.scala-lang.modules" %% "scala-xml" % "1.0.3",
  "com.orientechnologies" % "orientdb-graphdb" % "2.1-rc4",
  "org.scalatest" %% "scalatest" % "2.2.4" % "test"
)

fork in run := true
