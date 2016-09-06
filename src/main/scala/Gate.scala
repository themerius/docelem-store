package eu.themerius.docelemstore

import eu.themerius.docelemstore.utils.Stats.time

import akka.actor.{ Props, Actor }
import akka.routing.ActorRefRoutee
import akka.routing.Router
import akka.routing.RoundRobinRoutingLogic
import akka.event.Logging

import org.fusesource.stomp.jms._
import javax.jms._

import org.fusesource.stomp.client.Constants._
import org.fusesource.stomp.codec.StompFrame
import org.fusesource.stomp.client.Stomp

// Stomp Callbacks
import org.fusesource.stomp.client.Callback
import org.fusesource.stomp.client.CallbackConnection

import scala.collection.JavaConverters._

import com.typesafe.config.ConfigFactory

case class Consume(header: Map[String, String], message: String)
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

  consumer.setMessageListener(listener)


  val billing = new StompJmsDestination(brokerBilling)
  val accounting = session.createProducer(billing)

  val routerAccumuloFeeder = {
    val routees = Vector.fill(200) {
      val r = context.actorOf(Props[AccumuloFeeder])
      context.watch(r)
      ActorRefRoutee(r)
    }
    Router(RoundRobinRoutingLogic(), routees)
  }

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
      val event = header.getOrElse("event", "")
      val contentType = header.getOrElse("content-type", "")
      val replyTo = header.getOrElse("reply-to", "")
      var trackingNr = header.getOrElse("tracking-nr", "")

      (contentType, event) match {

        case ("gzip-xml", "ExtractRelations") => {

          log.info("(Gate) got gzipped XCAS and configure extraction of relations from sentences and paragraphs")

          val model = new GzippedXCasModel with ExtractParagraphs with ExtractSentences with ExtractRelations {
            override def applyRules = {
              val topologyArtifactsPar = paragraphs
                .zipWithIndex
                .map(t => genTopologyArtifact(t._1, t._2))
              val topologyArtifactsSent = paragraphs.map(sentences)
                .flatten.zipWithIndex
                .map(t => genTopologyArtifact(t._1._1, t._1._2, t._2))
              val searchArtifactsRels = sentences
                .map(relations).flatten
                .map(t => genSearchArtifact(t._1, t._2)).flatten
              val viewArtifactsRels = sentences.map(relations)
                .flatten.zipWithIndex
                .map(t => genViewArtifacts(t._1._1, t._1._2, t._2))
              val contentArtifactsRels = sentences.map(relations)
                .flatten.zipWithIndex
                .map(t => genContentArtifacts(t._1._1, t._1._2, t._2))
              val co = Corpus(
                topologyArtifactsPar ++ topologyArtifactsSent ++
                searchArtifactsRels ++ viewArtifactsRels ++
                contentArtifactsRels
              )
              println(co)
              co
            }
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
              val sentenceArtifacts = sentences.map(genContentArtifact)
              val outlineArtifacts = outlines.map(genContentArtifacts).flatten
              // hierarcy
              val topologyArtifacts = addTopologyTag(hierarchizedDocelems.map(genTopologyArtifact))
              // NNEs
              val nneArtifacts = sentences.map(nnes).flatten.map(t => genAnnotationArtifact(t._1, t._2) ).flatten
              // assemble Corpus
              Corpus(headerArtifacts ++ sentenceArtifacts ++ outlineArtifacts ++ topologyArtifacts ++ nneArtifacts)
            }
            override def getDocumentId = Some(sigmaticUri)
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

          // routerInMemory.route(
          //   Transform2DocElem(new SimpleWalLineModel, textContent.getBytes), sender()
          // )
          // routerAccumuloFeeder.route(
          //   Transform2DocElem(new SimpleWalLineModel, textContent.getBytes), sender()
          // )

        }

        case ("xml", "query-single-docelem") => {
          log.info("(Gate) got html and configure for query single docelem")
          val builder = new XmlModel with XmlSingleDocElemQueryBuilder
          val data = textContent.getBytes("UTF-8")
          val reply = Reply("", replyTo, trackingNr)
          accumuloQuery ! BuildQuery(builder, data, reply)
        }
        case ("xml", "query-topology") => {
          log.info("(Gate) got html and configure for query topology")
          val builder = new XmlModel with XmlTopologyQueryBuilder
          val data = textContent.getBytes("UTF-8")
          val reply = Reply("", replyTo, trackingNr)
          accumuloQuery ! BuildQuery(builder, data, reply)
        }
        case ("xml", "query-topology-only-hierarchy") => {
          log.info("(Gate) got html and configure for query topology only hierarchy")
          val builder = new XmlModel with XmlTopologyOnlyHierarchyQueryBuilder
          val data = textContent.getBytes("UTF-8")
          val reply = Reply("", replyTo, trackingNr)
          accumuloQuery ! BuildQuery(builder, data, reply)
        }
        case ("xml", "semantic-search") => {
          log.info("(Gate) got html and configure for query semantic search")
          val builder = new XmlModel with XmlSemanticSearchQueryBuilder
          val data = textContent.getBytes("UTF-8")
          val reply = Reply("", replyTo, trackingNr)
          accumuloQuery ! BuildQuery(builder, data, reply)
        }
        case (x, y) => {
          latestErrorLog = s"No rules for ($x, $y)."
          println(latestErrorLog)
        }
      }

    }

    case add: Add2Accumulo => {
      routerAccumuloFeeder.route(add, sender())
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
