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

// Stomp Callbacks
import org.fusesource.stomp.client.Callback
import org.fusesource.stomp.client.CallbackConnection

import scala.concurrent.duration._
import scala.concurrent.{ Future, Promise }
import scala.concurrent.Await
import scala.concurrent.TimeoutException
import scala.concurrent.ExecutionContext.Implicits.global

import scala.collection.JavaConverters._

import com.typesafe.config.ConfigFactory

case class Consume(header: Map[String, String], message: String)
case class Reply(content: String, to: String, trackingNr: String)
case class Accounting(event: String, query: String, trackingNr: String, unit: String)

class Gate extends Actor {

  println(s"Gate ${context.dispatcher}")

  var latestErrorLog = ""

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

  val receiveCallback = new Callback[StompFrame]() {
    override def onFailure(value: Throwable) {
      println(s"Receive Failed ${value}")
    }

    override def onSuccess(frame: StompFrame) {
      if (frame.action == MESSAGE) {
        // generate a list of properties and transform it into a standard map
        val headerMap = frame.headerList.asScala.map( entry =>
          entry.getKey.toString -> entry.getValue.toString
        ).toMap
        // send it to the gate actor to do further processing
        self ! Consume(headerMap, frame.contentAsString)
      }
    }
  }

  stomp.connectCallback(new Callback[CallbackConnection] {

    override def onFailure(value: Throwable) {
      println(s"Connection Failed ${value}")
    }

    override def onSuccess(connection: CallbackConnection) {
      println(s"Raw STOMP connection opened.")

      // register the callback which is triggered when a messages arrives
      connection.receive(receiveCallback)

      // setup on which queue should be listened
      connection.resume

      val frame = new StompFrame(SUBSCRIBE)
      frame.addHeader(DESTINATION, StompFrame.encodeHeader(brokerQueue))
      frame.addHeader(ID, connection.nextId)

      connection.request(frame, new Callback[StompFrame]() {
        override def onFailure(value: Throwable) {
          println(s"Receive Failed ${value}")
          connection.close(null)
        }

        override def onSuccess(value: StompFrame) {
          println(s"Raw STOMP connection listens to ${brokerQueue}.")
        }
      })
    }

  })


  val billing = new StompJmsDestination(brokerBilling)
  val accounting = session.createProducer(billing)

  // Create a router for balancing messages within the system
  val router = {
    val routees = Vector.fill(10) {
      val r = context.actorOf(Props[AccumuloTranslator])
      context.watch(r)
      ActorRefRoutee(r)
    }
    Router(RoundRobinRoutingLogic(), routees)
  }

  val accumuloFeeder = context.actorOf(Props[AccumuloFeeder])

  // Consume and distribute messages
  def receive = {
    case Consume(header: Map[String, String], textContent: String) => {
      // unwrap message and route it
      val event = header.getOrElse("event", "")
      val contentType = header.getOrElse("content-type", "")
      val replyTo = header.getOrElse("reply-to", "")
      var trackingNr = header.getOrElse("tracking-nr", "")

      (contentType, event) match {
        case ("gzip-xml", "ExtractNNEs") => {
          println("gzip-xml", "ExtractNNEs")
          accumuloFeeder ! Transform2DocElem(new GzippedXCasModel with ExtractNNEs, textContent.getBytes("UTF-8"))
          // Oder classOf[ExtractNNE].newInstance
        }
        case (x, y) => {
          latestErrorLog = s"No rules for ($x, $y)."
          println(latestErrorLog)
        }
      }

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
