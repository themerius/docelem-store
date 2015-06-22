package eu.themerius.docelemstore

import akka.actor.{ ActorRef, ActorSystem, Props, Actor, Inbox }

case class Get(uuid: String, version: Int = 0)
case class Get2(uuid: String, version: Int = 0)
case class GetFlatTopology(uuid: String)

case class Response(de: List[ActorRef])

class Store extends Actor {
  def receive = {
    case Get(uuid, version) => {
      println(s"Get and Create § $uuid in version $version.")
      val de = context.actorOf(Props[DocElem])
      sender ! Response(List(de))
    }
    case Get2(uuid, version) => {
      println(s"Get § $uuid in version $version.")
      //sender ! s"hiho ${uuid}"
    }
    case GetFlatTopology(uuid) => {
      println(s"Get flat topo for § $uuid.")
      val lst = List.fill(10)(context.actorOf(Props[DocElem]))
      sender ! Response(lst)
    }
    case other => println("Can't handle " + other)
  }
}

case class Projection(p: String)

class DocElem extends Actor {
  def receive = {
    case Projection(p) if p == "Html" => sender ! "<p>Test</p>"
    case Projection(p) if p == "PlainText" => sender ! "Test"
    case other => println("Can't handle " + other)
  }
}
