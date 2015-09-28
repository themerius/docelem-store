package eu.themerius.docelemstore

import akka.actor.{ ActorRef, ActorSystem, Props, Actor, Inbox }
import akka.routing.ActorRefRoutee
import akka.routing.Router
import akka.routing.RoundRobinRoutingLogic
import scala.concurrent.duration._

import org.fusesource.stomp.jms._
import javax.jms._

case object Consume

class Gate extends Actor {

  // Connect to the broker
  val factory = new StompJmsConnectionFactory
  val brokerURI = "tcp://ashburner:61613"
  factory.setBrokerURI(brokerURI)
  val connection = factory.createConnection("admin", "password")
  connection.start
  val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
  val dest = new StompJmsDestination("/queue/docelem-store")
  val consumer = session.createConsumer(dest)

  // Create router for balancing messages within the system
  val router = {
    val routees = Vector.fill(5) {
      val r = context.actorOf(Props[AccumuloTranslator])
      context.watch(r)
      ActorRefRoutee(r)
    }
    Router(RoundRobinRoutingLogic(), routees)
  }

  def receive = {
    case Consume => {
      val message = consumer.receive  // block until getting a message
      if (message != null) {  // unwrap message and route it
        val textContent = message.asInstanceOf[TextMessage].getText
        val event = message.getStringProperty("event")
        event match {
          case "FoundCorpus" => router.route(FoundCorpus(textContent), sender())
          case _ => println("Undefined event.")
        }
      }
      self ! Consume
    }
  }

}
