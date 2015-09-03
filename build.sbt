name := """docelem-store"""

version := "1.0"

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.11",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.11",
  "com.typesafe.akka" %% "akka-remote" % "2.3.11",
  "org.scala-lang.modules" %% "scala-xml" % "1.0.3",
  "com.orientechnologies" % "orientdb-graphdb" % "2.1-rc4",
  //"com.orientechnologies" % "orientdb-lucene" % "2.1-rc4",
  "com.orientechnologies" % "orientdb-jdbc" % "2.1-rc4",
  "org.apache.lucene" % "lucene-core" % "5.2.1",
  "org.apache.lucene" % "lucene-analyzers-common" % "5.2.1",
  "org.apache.lucene" % "lucene-queryparser" % "5.2.1",
  "org.scalatest" %% "scalatest" % "2.2.4" % "test"
)

fork in run := true
