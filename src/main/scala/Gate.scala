package eu.themerius.docelemstore

import eu.themerius.docelemstore.utils.Stats.time

import akka.actor.{ ActorRef, ActorSystem, Props, Actor, Inbox }
import akka.routing.ActorRefRoutee
import akka.routing.Router
import akka.routing.RoundRobinRoutingLogic
import scala.concurrent.duration._

import org.fusesource.stomp.jms._
import javax.jms._

import scala.concurrent.duration._
import scala.concurrent.{ Future, Promise }
import scala.concurrent.Await
import scala.concurrent.TimeoutException
import scala.concurrent.ExecutionContext.Implicits.global

case class Consume(message: TextMessage)
case class Reply(content: String, to: String, trackingNr: String)
case class Accounting(event: String, query: String, trackingNr: String, unit: String)

class Gate extends Actor {

  // Connect to the broker
  val factory = new StompJmsConnectionFactory
  val brokerURI = "tcp://ashburner:61613"
  factory.setBrokerURI(brokerURI)
  val connection = factory.createConnection("admin", "password")
  connection.start
  val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
  val dest = new StompJmsDestination("/queue/docElemStore")
  val consumer = session.createConsumer(dest)

  val billing = new StompJmsDestination("/topic/billing")
  val accounting = session.createProducer(billing)

  // Create a router for balancing messages within the system
  val router = {
    val routees = Vector.fill(5) {
      val r = context.actorOf(Props[AccumuloTranslator])
      context.watch(r)
      ActorRefRoutee(r)
    }
    Router(RoundRobinRoutingLogic(), routees)
  }

  // Start listening in extra "thread"
  Future {
    while (true) {
      // block until getting a message
      val message = consumer.receive
      if (message != null) {
        val textMessage = message.asInstanceOf[TextMessage]
        self ! Consume(textMessage)
      }
    }
  }

  // Consume and distribute messages
  def receive = {
    case Consume(message: TextMessage) => {
      // unwrap message and route it
      val textContent = message.getText
      val event = message.getStringProperty("event")
      val replyTo = message.getJMSReplyTo
      var trackingNr = message.getStringProperty("tracking-nr")
      if (trackingNr == null) trackingNr = ""
      event match {
        case "FoundCorpus" => {
          router.route(FoundCorpus(textContent), sender())
          self ! Accounting(event, "", trackingNr, "1 corpus")
        }
        case "QueryDocelem" => {
          router.route(QueryDocelem(textContent, replyTo.toString, trackingNr), sender())
          self ! Accounting(event, textContent, trackingNr, "1 query")
        }
        case _ => {
          println("Undefined event.")
        }
      }
    }

    case Reply(content, to, trackingNr) => time (s"Gate:Reply($to)") {
      val destination = new StompJmsDestination(to)
      val producer = session.createProducer(destination)
      val message = session.createTextMessage(content)
      message.setStringProperty("tracking-nr", trackingNr)
      producer.send(message)
    }

    case Accounting(event, query, trackingNr, unit) => {
      val message = session.createTextMessage(query)
      message.setStringProperty("event", event)
      message.setStringProperty("agent", "docelem-store")
      message.setStringProperty("tracking-nr", trackingNr)
      message.setJMSTimestamp(System.currentTimeMillis)
      message.setStringProperty("unit", unit)
      accounting.send(message)
    }

    case unknown => println("Gate got a unknown message: " + unknown)
  }

}
