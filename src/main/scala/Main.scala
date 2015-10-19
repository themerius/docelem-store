package eu.themerius.docelemstore

import akka.actor.{ ActorRef, ActorSystem, Props, Actor, Inbox }
import scala.concurrent.duration._

// For the ask pattern
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.Await

import eu.themerius.docelemstore.utils.Stats.time

object DocElemStore extends App {
  // Start Akka system
  val system = ActorSystem("docelem-store")
  // Start one Storage actor (you should only start 1..1)
  val storage = system.actorOf(Props[AccumuloStorage], "accumulo-storage")
  // Start one Gate actor (you can start 1..n)
  system.actorOf(Props[Gate], "gate-1")

  // Schedule a FLUSH after 5s every 10s.
  // Only needed for testing...
  import system.dispatcher
  system.scheduler.schedule(5000.milliseconds,
    10000.milliseconds,
    storage,
    "FLUSH")
}
