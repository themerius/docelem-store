package eu.themerius.docelemstore

import eu.themerius.docelemstore.utils.Stats.time

import akka.actor.{ Props, Actor }
import akka.routing.ActorRefRoutee
import akka.routing.Router
import akka.routing.RoundRobinRoutingLogic
import akka.event.Logging

import org.fusesource.stomp.jms._
import javax.jms._

import java.net.URI

import org.fusesource.stomp.client.Constants._
import org.fusesource.stomp.codec.StompFrame
import org.fusesource.stomp.client.Stomp

// Stomp Callbacks
import org.fusesource.stomp.client.Callback
import org.fusesource.stomp.client.CallbackConnection

import scala.collection.JavaConverters._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.TimeoutException

import com.typesafe.config.ConfigFactory

case class Consume(header: Map[String, String], message: String)
case class CustomModel(msg: String, model: ModelTransRules)
case class Reply(content: String, to: String, trackingNr: String)
case class Accounting(event: String, query: String, trackingNr: String, unit: String)

class Gate extends Actor {

  val log = Logging(context.system, this)
  log.info(s"Starting ${BuildInfo.name}:${BuildInfo.version}.")

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

  val elistener = new ExceptionListener {
    override def onException(jmse: JMSException) {
      log.error(s"Connection Error: $jmse")
      // TODO: cleanup connection, retry connection and don't die... :)
      log.warning("Because I'm depressed of the connection error, I'll doing suicide!")
      System.exit(1)
    }
  }

  val connection = factory.createConnection(brokerUsr, brokerPwd)
  connection.setExceptionListener(elistener);
  connection.setClientID(s"jms.topic.${java.util.UUID.randomUUID.toString}")
  connection.start

  val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)

  val queue = new StompJmsDestination(brokerQueue)
  val consumer = session.createConsumer(queue)

  val listener = new MessageListener {

    override def onMessage(message: Message) {

      val headers = for(prop <- message.getPropertyNames.asScala) yield {
        val strProp = prop.asInstanceOf[String]
        strProp -> message.getStringProperty(strProp)
      }

      val replyTo = if (message.getJMSReplyTo != null) {
        message.getJMSReplyTo.toString
      } else {
        ""
      }

      val headerMap = headers.toMap.updated("reply-to", replyTo)

      if (message.isInstanceOf[TextMessage]) {
        self ! Consume(headerMap, message.asInstanceOf[TextMessage].getText)
      }

    }

  }

  // consumer.setMessageListener(listener)

  // Blocking alternative to message listener.
  // This is a easy way to handle back pressure.
  Future {
    while(true) {
      val message = consumer.receive  // block until getting a message
      if (message != null) {  // unwrap message and route it
        val headers = for(prop <- message.getPropertyNames.asScala) yield {
          val strProp = prop.asInstanceOf[String]
          strProp -> message.getStringProperty(strProp)
        }

        val replyTo = if (message.getJMSReplyTo != null) {
          message.getJMSReplyTo.toString
        } else {
          ""
        }

        val headerMap = headers.toMap.updated("reply-to", replyTo)

        if (message.isInstanceOf[TextMessage]) {
          self ! Consume(headerMap, message.asInstanceOf[TextMessage].getText)
        }
      }
    }
  }


  val billing = new StompJmsDestination(brokerBilling)
  val accounting = session.createProducer(billing)

  val routerAccumuloFeeder = {
    val routees = Vector.fill(100) {
      val r = context.actorOf(Props[AccumuloFeeder].withMailbox("akka.actor.blocking-mailbox"))
      context.watch(r)
      ActorRefRoutee(r)
    }
    Router(RoundRobinRoutingLogic(), routees)
  }

  val inMemory = context.actorOf(Props[InMemoryStore])
  // var UNSCHOENER_HACK: akka.actor.ActorRef = null
  //
  // val routerInMemory = {
  //   val routees = Vector.fill(1) {
  //     val r = context.actorOf(Props[InMemoryStore])
  //     UNSCHOENER_HACK = r
  //     context.watch(r)
  //     ActorRefRoutee(r)
  //   }
  //   Router(RoundRobinRoutingLogic(), routees)
  // }

  val accumuloQuery = context.actorOf(Props[AccumuloQueryer])
  // val accumuloQuery = UNSCHOENER_HACK

  // Consume and distribute messages
  def receive = {
    case Consume(header: Map[String, String], textContent: String) => {
      // unwrap message and route it
      var event = header.getOrElse("event", "")
      val contentType = header.getOrElse("content-type", "")
      val replyTo = header.getOrElse("reply-to", "")
      val trackingNr = header.getOrElse("tracking-nr", "")
      var documentId = header.get("document-id")
      var documentLabel = header.get("document-label")

      (contentType, event) match {

        case ("gzip-xml", "onlyStoreXMI") => {

          log.info("(Gate) got gzipped XCAS, simply write into Accumulo.")

          val model = new Model with ModelTransRules {
            def deserialize(m: Array[Byte]) = this
            def serialize: Array[Byte] = Array[Byte]()
            override def getDocumentId = documentId
            override def getDocumentLabel = documentLabel
            override def rawTextMiningData: Option[RawData] = Some(RawData("gzip_xmi", textContent.getBytes))
          }

          routerAccumuloFeeder.route(
            Transform2DocElem(model, textContent.getBytes), sender()
          )

        }

        case ("gzip-xml", _) => {

          log.info("(Gate) got gzipped XCAS, configure for document extraction only")

          val model = new GzippedXCasModel with ExtractHeader with ExtractGenericHierarchy with ExtractSentences with ExtractOutlines with ExtractNNEs {
            override def applyRules = {
              // header, setences, ...
              val headerArtifacts = genContentArtifacts(header)
              val sentenceArtifacts = sentences.map(genContentArtifact).filterNot(ar => ar.sigmatics == new URI(""))
              val outlineArtifacts = outlines.map(genContentArtifacts).flatten
              // hierarcy
              val topologyArtifacts = addTopologyTag(hierarchizedDocelems.map(genTopologyArtifact)).filterNot(ar => ar.sigmatics == new URI(""))
              // NNEs
              val nneArtifacts = sentences.map(nnes).flatten.map(t => genAnnotationArtifact(t._1, t._2) ).flatten
              // assemble Corpus
              Corpus(headerArtifacts ++ sentenceArtifacts ++ outlineArtifacts ++ topologyArtifacts ++ nneArtifacts)
            }
            override def getDocumentId = Some(headerRawId)
            override def getDocumentLabel = Some(sigmaticUri)
            override def rawTextMiningData: Option[RawData] = Some(RawData("gzip_xmi", textContent.getBytes))
            override def rawPlaintextData: Option[RawData] = {
              if (getDocumentViewText.nonEmpty && getDocumentViewSpec.nonEmpty) {
                Some(RawData(getDocumentViewSpec.get, getDocumentViewText.get.getBytes))
              } else {
                None
              }
            }
            override def rawOriginalData: Option[RawData] = {
              if (getInitialViewText.nonEmpty && getInitialViewSpec.nonEmpty) {
                Some(RawData(getInitialViewSpec.get, getInitialViewText.get.getBytes))
              } else {
                None
              }
            }
          }

          // routerInMemory.route(
          //   Transform2DocElem(model, textContent.getBytes), sender()
          // )
          routerAccumuloFeeder.route(
            Transform2DocElem(model, textContent.getBytes), sender()
          )

        }

        case ("wal-line", "") => {

          log.info("(Gate) got WAL line")

          inMemory ! Transform2DocElem(new SimpleWalLineModel, textContent.getBytes)

          routerAccumuloFeeder.route(
            Transform2DocElem(new SimpleWalLineModel, textContent.getBytes, true), sender()
          )

        }

        case ("xml", "query-single-docelem") => {
          log.info("(Gate) got html and configure for query single docelem")
          val builder = new XmlModel with XmlSingleDocElemQueryBuilder
          val data = textContent.getBytes("UTF-8")
          val reply = Reply("", replyTo, trackingNr)
          accumuloQuery ! BuildQuery(builder, data, reply)
          inMemory ! BuildQuery(builder, data, reply)
        }
        case ("xml", "query-topology") => {
          log.info("(Gate) got html and configure for query topology")
          val builder = new XmlModel with XmlTopologyQueryBuilder
          val data = textContent.getBytes("UTF-8")
          val reply = Reply("", replyTo, trackingNr)
          accumuloQuery ! BuildQuery(builder, data, reply)
          inMemory ! BuildQuery(builder, data, reply)
        }
        case ("xml", "query-topology-only-hierarchy") => {
          log.info("(Gate) got html and configure for query topology only hierarchy")
          val builder = new XmlModel with XmlTopologyOnlyHierarchyQueryBuilder
          val data = textContent.getBytes("UTF-8")
          val reply = Reply("", replyTo, trackingNr)
          accumuloQuery ! BuildQuery(builder, data, reply)
          inMemory ! BuildQuery(builder, data, reply)
        }
        case ("xml", "semantic-search") => {
          log.info("(Gate) got html and configure for query semantic search")
          val builder = new XmlModel with XmlSemanticSearchQueryBuilder
          val data = textContent.getBytes("UTF-8")
          val reply = Reply("", replyTo, trackingNr)
          accumuloQuery ! BuildQuery(builder, data, reply)
          inMemory ! BuildQuery(builder, data, reply)
        }
        case (x, y) => {
          latestErrorLog = s"No rules for ($x, $y)."
          println(latestErrorLog)
        }
      }

    }

    case add: Add2Accumulo => {
      log.info(s"(Gate/Add2Accumulo) sending ${add} to Accumulo.")
      routerAccumuloFeeder.route(add, sender())
    }

    case CustomModel("wal", model) => {

      log.info("(Gate) got WAL")

      inMemory ! Transform2DocElem(model, Array[Byte]())

      routerAccumuloFeeder.route(
        Transform2DocElem(model, Array[Byte](), true), sender()
      )

    }

    case Reply(content, to, trackingNr) => time (s"Gate:Reply($to)") {
      val destination = new StompJmsDestination(to)
      val producer = session.createProducer(destination)
      val message = session.createTextMessage(content)
      message.setStringProperty("tracking-nr", trackingNr)
      producer.send(message)
      producer.close
      log.info(s"(Gate/Reply) send ${trackingNr} back to broker at ${to}.")
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
