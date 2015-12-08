package eu.themerius.docelemstore

import eu.themerius.docelemstore.utils.Stats.time

import akka.actor.{ ActorRef, ActorSystem, Props, Actor, Inbox }
import akka.routing.ActorRefRoutee
import akka.routing.Router
import akka.routing.RoundRobinRoutingLogic
import scala.concurrent.duration._

import org.fusesource.stomp.jms._
import javax.jms._

import org.fusesource.stomp.client.Constants._
import org.fusesource.stomp.codec.StompFrame
import org.fusesource.stomp.client.Stomp

import scala.concurrent.duration._
import scala.concurrent.{ Future, Promise }
import scala.concurrent.Await
import scala.concurrent.TimeoutException
import scala.concurrent.ExecutionContext.Implicits.global

import scala.collection.JavaConverters._

import com.typesafe.config.ConfigFactory

case object ReceiveFromBroker
case class Consume(header: Map[String, String], message: String)
case class Reply(content: String, to: String, trackingNr: String)
case class Accounting(event: String, query: String, trackingNr: String, unit: String)

class Gate extends Actor {

  println(s"Gate ${context.dispatcher}")

  val conf = ConfigFactory.load
  val brokerUri = conf.getString("docelem-store.broker.uri")
  val brokerUsr = conf.getString("docelem-store.broker.usr")
  val brokerPwd = conf.getString("docelem-store.broker.pwd")
  val brokerQueue = conf.getString("docelem-store.broker.queue")
  val brokerBilling = conf.getString("docelem-store.broker.billing")

  // Connect to the broker
  val factory = new StompJmsConnectionFactory
  factory.setBrokerURI(brokerUri)
  val connection = factory.createConnection(brokerUsr, brokerPwd)
  connection.start
  val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)

  // Raw STOMP connection
  val stomp = new Stomp(brokerUri)
  stomp.setLogin(brokerUsr)
  stomp.setPasscode(brokerPwd)
  val consumer = stomp.connectBlocking

  // Raw STOMP listen
  val frame = new StompFrame(SUBSCRIBE)
  frame.addHeader(DESTINATION, StompFrame.encodeHeader(brokerQueue))
  frame.addHeader(ID, consumer.nextId)
  val response = consumer.request(frame)
  println(s"Raw STOMP connection listens to ${brokerQueue}.")


  val billing = new StompJmsDestination(brokerBilling)
  val accounting = session.createProducer(billing)

  // Create a router for balancing messages within the system
  val router = {
    val routees = Vector.fill(2) {
      val r = context.actorOf(Props[AccumuloTranslator])
      context.watch(r)
      ActorRefRoutee(r)
    }
    Router(RoundRobinRoutingLogic(), routees)
  }

  // Consume and distribute messages
  def receive = {
    case ReceiveFromBroker => {
      val frame = consumer.receive
      if (frame.action == MESSAGE) {
        val headerMap = frame.headerList.asScala.map( x =>
          x.getKey.toString -> x.getValue.toString
        ).toMap
        self ! Consume(headerMap, frame.contentAsString)
      }

      self ! ReceiveFromBroker
    }

    case Consume(header: Map[String, String], textContent: String) => {
      // unwrap message and route it
      val event = header.getOrElse("event", "")
      val replyTo = header.getOrElse("reply-to", "")
      var trackingNr = header.getOrElse("tracking-nr", "")
      event match {
        case "FoundCorpus" => {
          router.route(FoundCorpus(textContent), sender())
          //self ! Accounting(event, "", trackingNr, "1 corpus")
        }
        case "QueryDocelem" => {
          router.route(QueryDocelem(textContent, replyTo.toString, trackingNr), sender())
          //self ! Accounting(event, textContent, trackingNr, "1 query")
        }
        case "QueryAnnotationIndex" => {
          router.route(QueryAnnotationIndex(textContent, replyTo.toString, trackingNr), sender())
          //self ! Accounting(event, textContent, trackingNr, "1 query")
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
