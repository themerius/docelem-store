package eu.themerius.docelemstore

import akka.actor.{ ActorRef, ActorSystem, Props, Actor, Inbox }
import scala.concurrent.duration._

// For the ask pattern
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.Await

import eu.themerius.docelemstore.utils.Stats.time

object DES2 extends App {
  val system = ActorSystem("docelem-store")

  val storage = system.actorOf(Props[AccumuloStorage], "accumulo-storage")

  val gate = system.actorOf(Props[Gate], "gate-1")
  gate ! Consume

}

object DocElemStore extends App {

  // Start Akka
  val system = ActorSystem("docelem-store")

  // Create the 'greeter' actor
  val store = system.actorOf(Props[Store], "store")
  println(store)

  store ! Init("/About.xml")
  store ! Init("/MedLineAbstracts5k.xml")
  //store ! Init("/MedLineAbstracts100k.xml")

  val inbox = Inbox.create(system)
  implicit val timeout = Timeout(120.seconds)

  // get result of one DocElem
  // time {
  //   inbox.send(store, Get("23664431"))
  //   val Response(de) = inbox.receive(5.seconds)
  //   val msg = de(0) ? Projection("Html")
  //   println(Await.result(msg, timeout.duration).asInstanceOf[String])
  // }

  // get result of one DocElem
  inbox.send(store, GetSimple("23664431"))
  val ResponsePayload(de) = inbox.receive(60.seconds)
  println(de)

  inbox.send(store, GetSimple("23664431"))
  val ResponsePayload(de1) = inbox.receive(60.seconds)
  println(de1)

  inbox.send(store, GetSimple("23664431"))
  val ResponsePayload(de2) = inbox.receive(60.seconds)
  println(de2)

  // Get result of multiple DocElems
  // time {
  //   inbox.send(store, GetFlatTopology("about-002"))
  //   val Response(des) = inbox.receive(5.seconds)
  //   val msgs = des.view.map(_ ? Projection("Html"))
  //   val awaited = msgs.map(Await.result(_, timeout.duration).asInstanceOf[String])
  //   println(awaited.force)
  // }

  // val dep = DocElemPayload("person-001", "Person", "Sven Hodapp")
  // store ! Create(dep :: Nil)

  val dep = DocElemPayload("person-001", "Person", "Sven Hodapp")
  inbox.send(store, GetOrCreate(dep :: Nil))
  val ResponsePayload(depPerson) = inbox.receive(120.seconds)
  println("GetOrCreate -> Actor -> " + depPerson)

  def a(i: Int) = {
    inbox.send(store, Get("23664431"))
    val Response(de3) = inbox.receive(120.seconds)
    de3(0) ! AnnotateWith("person-001", "has_provanance" + i)
  }

  a(1)
  a(2)
  a(3)

  system.shutdown

}
