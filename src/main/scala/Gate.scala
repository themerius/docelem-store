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
  val connection = factory.createConnection(brokerUsr, brokerPwd)
  connection.start
  val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)

  // Raw STOMP connection
  val stomp = new Stomp(brokerUri)
  stomp.setLogin(brokerUsr)
  stomp.setPasscode(brokerPwd)

  val receiveCallback = new Callback[StompFrame]() {
    override def onFailure(value: Throwable) = {
      println(s"Receive Failed ${value}")
    }

    override def onSuccess(frame: StompFrame) = {
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

    override def onFailure(value: Throwable) = {
      println(s"Connection Failed ${value}")
    }

    override def onSuccess(connection: CallbackConnection) = {
      println(s"Raw STOMP connection opened.")

      // register the callback which is triggered when a messages arrives
      connection.receive(receiveCallback)

      // setup on which queue should be listened
      connection.resume

      val frame = new StompFrame(SUBSCRIBE)
      frame.addHeader(DESTINATION, StompFrame.encodeHeader(brokerQueue))
      frame.addHeader(ID, connection.nextId)

      connection.request(frame, new Callback[StompFrame]() {
        override def onFailure(value: Throwable) = {
          println(s"Receive Failed ${value}")
          connection.close(null)
        }

        override def onSuccess(value: StompFrame) = {
          println(s"Raw STOMP connection listens to ${brokerQueue}.")
        }
      })
    }

  })


  val billing = new StompJmsDestination(brokerBilling)
  val accounting = session.createProducer(billing)

  // Create a router for balancing messages within the system
  val router = {
    val routees = Vector.fill(1) {
      val r = context.actorOf(Props[AccumuloTranslator])
      context.watch(r)
      ActorRefRoutee(r)
    }
    Router(RoundRobinRoutingLogic(), routees)
  }

  val routerF = {
    val routees = Vector.fill(1) {
      val r = context.actorOf(Props[AccumuloFeeder])
      context.watch(r)
      ActorRefRoutee(r)
    }
    Router(RoundRobinRoutingLogic(), routees)
  }

  var UNSCHOENER_HACK: akka.actor.ActorRef = null

  val routerIM = {
    val routees = Vector.fill(1) {
      val r = context.actorOf(Props[InMemoryStore])
      UNSCHOENER_HACK = r
      context.watch(r)
      ActorRefRoutee(r)
    }
    Router(RoundRobinRoutingLogic(), routees)
  }

  //val accumuloQuery = context.actorOf(Props[AccumuloQueryer])
  val accumuloQuery = UNSCHOENER_HACK

  // Consume and distribute messages
  def receive = {
    case Consume(header: Map[String, String], textContent: String) => {
      // unwrap message and route it
      val event = header.getOrElse("event", "")
      val contentType = header.getOrElse("content-type", "")
      val replyTo = header.getOrElse("reply-to", "")
      var trackingNr = header.getOrElse("tracking-nr", "")

      (contentType, event) match {

        case ("gzip-xml", "ExtractHeader ExtractFrontMatter") => {

          log.info("(Gate) got gzipped XCAS and configure extraction of front matter with sentences from header")

          val model = new GzippedXCasModel with ExtractHeader with ExtractFrontMatter with ExtractSentences {
            override def applyRules = {
              val contentArtifactsHeader = genContentArtifacts(header)
              val topologyArtifactsMatter = frontMatters
                .map(genTopologyArtifact)
              val topologyArtifactsTitle = frontMatters
                .map(documentTitles).flatten
                .map(t => genTopologyArtifact(t._1, t._2))
              val topologyArtifactsAbstr = frontMatters
                .map(documentAbstracts).flatten
                .map(t => genTopologyArtifact(t._1, t._2))
              val topologyArtifactsSentOnAbstract = frontMatters
                .map(documentAbstracts).flatten.map(t => sentences(t._1))
                .flatten.zipWithIndex
                .map(t => genTopologyArtifact(t._1._1, t._1._2, t._2))
              val topologyArtifactsSentOnTitle = frontMatters
                .map(documentTitles).flatten.map(t => sentences(t._1))
                .flatten.zipWithIndex
                .map(t => genTopologyArtifact(t._1._1, t._1._2, t._2))
              val contentArtifactsSent = sentences
                .map(genContentArtifact)
              val contentArtifactsTitle = documentTitles
                .map(genContentArtifact)
              val co = Corpus(
                contentArtifactsHeader ++ topologyArtifactsMatter ++
                topologyArtifactsTitle ++ topologyArtifactsAbstr ++
                topologyArtifactsSentOnAbstract ++ topologyArtifactsSentOnTitle ++
                contentArtifactsTitle ++ contentArtifactsSent
              )
              co
            }
          }

          routerF.route(
            Transform2DocElem(model, textContent.getBytes), sender()
          )

        }

        case ("xmi", "ExtractCtgovUseCase") => {

          log.info("(Gate) XMI and configure extraction of the CTGOV use case")

          // TODO: extract also sentences and paragraphs?
          // TODO: refactor sentence/paragraphs so that they use the generic hierarchy trait
          // TODO: refactor sections trait, such that it is more generic (e.g. using Outline type)

          val model = new XCasModel with ExtractHeader with ExtractSections with ExtractLists with ExtractGenericHierarchy with ExtractSentences {
            override def applyRules = {
              val headerArtifacts = genContentArtifacts(header)
              val sectionsArtifacts = sections.map(genContentArtifacts).flatten
              val subSectionsArtifacts = subSections.map(genContentArtifacts).flatten
              val listAritfacts = lists.map(genContentArtifact)
              val sentenceArtifacts = sentences.map(genContentArtifact)
              val topologyArtifacts = hierarchizedDocelems.map(genTopologyArtifact)
              Corpus(headerArtifacts ++ sectionsArtifacts ++ subSectionsArtifacts ++ listAritfacts ++ sentenceArtifacts ++ topologyArtifacts)
            }
          }

          routerIM.route(
            Transform2DocElem(model, textContent.getBytes), sender()
          )

        }

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

          routerF.route(
            Transform2DocElem(model, textContent.getBytes), sender()
          )

        }

        case ("gzip-xml", "") => {

          log.info("(Gate) got gzipped XCAS, configure for document extraction only")

          val model = new GzippedXCasModel with ExtractHeader with ExtractGenericHierarchy with ExtractSentences {
            override def applyRules = {
              val headerArtifacts = genContentArtifacts(header)
              val sentenceArtifacts = sentences.map(genContentArtifact)
              val topologyArtifacts = hierarchizedDocelems.map(genTopologyArtifact)
              Corpus(headerArtifacts ++ sentenceArtifacts ++ topologyArtifacts)
            }
          }

          routerF.route(
            Transform2DocElem(model, textContent.getBytes), sender()
          )

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
        case (x, y) => {
          latestErrorLog = s"No rules for ($x, $y)."
          println(latestErrorLog)
        }
      }

      // TODO: integrate that into the other match/case
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
