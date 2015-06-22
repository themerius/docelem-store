package eu.themerius.docelemstore

import akka.actor.{ ActorRef, ActorSystem, Props, Actor, Inbox }
import scala.concurrent.duration._

// For the ask pattern
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.Await

object DocElemStore extends App {

  val system = ActorSystem("docelem-store")

  // Create the 'greeter' actor
  val store = system.actorOf(Props[Store], "store")
  println(store)

  store ! Get2("about-001")
  store ! Get2("about-002", 11)

  implicit val inbox = Inbox.create(system)
  inbox.send(store, Get("about-003"))
  val Response(de) = inbox.receive(5.seconds)
  println(de)

  implicit val timeout = Timeout(5.seconds)

  val msg = de(0) ? Projection("Html")
  println(Await.result(msg, timeout.duration).asInstanceOf[String])

  // Test for multiple
  inbox.send(store, GetFlatTopology("about-003"))
  val Response(des) = inbox.receive(5.seconds)
  val msgs = des.view.map(_ ? Projection("PlainText"))
  val awaited = msgs.map(Await.result(_, timeout.duration).asInstanceOf[String])
  println(awaited.force)

  //system.shutdown

}
