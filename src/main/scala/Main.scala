package eu.themerius.docelemstore

import akka.actor.{ ActorRef, ActorSystem, Props, Actor, Inbox }
import scala.concurrent.duration._

// For the ask pattern
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.Await

import eu.themerius.docelemstore.utils.Stats.time

object DocElemStore extends App {

  // Start Akka
  val system = ActorSystem("docelem-store")

  // Create the 'greeter' actor
  val store = system.actorOf(Props[Store], "store")
  println(store)

  store ! Init("/About.xml")
  store ! Init("/MedLineAbstracts100k.xml")

  val inbox = Inbox.create(system)
  implicit val timeout = Timeout(5.seconds)

  // get result of one DocElem
  // time {
  //   inbox.send(store, Get("23664431"))
  //   val Response(de) = inbox.receive(5.seconds)
  //   val msg = de(0) ? Projection("Html")
  //   println(Await.result(msg, timeout.duration).asInstanceOf[String])
  // }

  // Get result of multiple DocElems
  // time {
  //   inbox.send(store, GetFlatTopology("about-002"))
  //   val Response(des) = inbox.receive(5.seconds)
  //   val msgs = des.view.map(_ ? Projection("Html"))
  //   val awaited = msgs.map(Await.result(_, timeout.duration).asInstanceOf[String])
  //   println(awaited.force)
  // }

  time ("Create:Person") {
    val dep = DocElemPayload("person-001", "Person", "Sven Hodapp")
    store ! Create(dep :: Nil)
  }

  time ("Add:Prov") {
    inbox.send(store, Get("23664431"))
    val Response(de) = inbox.receive(20.seconds)
    de(0) ! OfProvenance("person-001")
  }

  system.shutdown

}
