package eu.themerius.docelemstore

import akka.actor.{ ActorRef, ActorSystem, Props, Actor, Inbox }
import scala.concurrent.duration._

// For the ask pattern
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.Await

import java.nio.file.{ Files, Paths }

import com.typesafe.config.ConfigFactory

import eu.themerius.docelemstore.utils.Stats.time

object DocElemStore extends App {
  // Workaround if no external config file is set
  if (System.getProperty("config.file") == "null") {
    System.clearProperty("config.file")
  }

  // Load config
  val conf = ConfigFactory.load

  // Start Akka system
  val system = ActorSystem("docelem-store")
  // Start one Storage actor (you should only start 1..1)
  val storage = system.actorOf(Props[AccumuloStorage], "accumulo-storage")
  // Start one Gate actor (you can start 1..n)
  val number = conf.getInt("docelem-store.gate.number") + 1
  for (i <- 1 until number) {
    val gate = system.actorOf(Props[Gate], s"gate-$i")
    fillExamples(gate)
    println(s"Starting gate-$i")
  }

  def getSampleXCAS = {
    val uri = getClass.getResource("/Version28-06.xml").toURI // System.getProperty("xmi.path")
    val bytes = Files.readAllBytes(Paths.get(uri))
    new String(bytes, "UTF-8")
  }

  def fillExamples(gate: ActorRef) = {
    val header = Map(
      "content-type" -> "xmi",
      "event" -> "ExtractCtgovUseCase"
    )
    gate ! Consume(header, getSampleXCAS)
  }

}
