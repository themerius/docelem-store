package eu.themerius.docelemstore

import org.scalatest.{ BeforeAndAfterAll, FlatSpecLike, Matchers }
import akka.actor.{ Props, ActorSystem }
import akka.testkit.{ ImplicitSender, TestKit, TestActorRef }
import scala.concurrent.duration._

import akka.testkit.EventFilter
import com.typesafe.config.ConfigFactory

import java.nio.file.{ Files, Paths }

class GateActorSpec(_system: ActorSystem)
  extends TestKit(_system)
  with ImplicitSender
  with Matchers
  with FlatSpecLike
  with BeforeAndAfterAll {

  def this() = this(ActorSystem("testsystem", ConfigFactory.parseString("""
    akka.loggers = ["akka.testkit.TestEventListener"]
    akka.loglevel = "DEBUG"
  """)))

  override def afterAll: Unit = {
    system.shutdown()
    system.awaitTermination(10.seconds)
  }

  def getSampleXCAS = {
    val uri = getClass.getResource("/sample-gzip-xcas").toURI
    val bytes = Files.readAllBytes(Paths.get(uri))
    new String(bytes, "UTF-8")
  }

  // TODO: send sample message to broker
  // TODO: receive message from broker and send to actor

  "An Gate" should "be able to consume a gzipped XCAS" in {
    val gate = TestActorRef(Props[Gate])

    val header = Map(
      "content-type" -> "gzip-xml",
      "event" -> "ExtractNNEs"
    )

    EventFilter.info(pattern="got gzipped XCAS and configure for NNE extraction", occurrences=1) intercept {
      EventFilter.info(pattern="has written 42 mutations", occurrences=2) intercept {
        gate ! Consume(header, getSampleXCAS)
      }
    }

  }

  it should "be able reply on queries for single document elements" in {
    val gate = TestActorRef(Props[Gate])

    val header = Map(
      "content-type" -> "xml",
      "event" -> "query-single-docelem",
      "reply-to" -> "/topic/docelem-test-case",
      "tracking-nr" -> "docelem-test"
    )

    val html =
      <head>
        <meta name="event" content="query-single-docelem" />
        <meta name="docelem-id" content="header/pmid:161461" />
        <meta name="license" content="" />
      </head>

      // IDEA: enrich with RDFa etc. -> The query interpreter should be able to interpret this?
      // val freetextQuery = <p>I would like to have the complete document element §header/pmid:161461.</p>

    EventFilter.debug(message=s"""Query(SingleDocElem,<query><meta content="header/pmid:161461" name="docelem-id"/></query>)""", occurrences=1) intercept {
      gate ! Consume(header, html.toString)
    }

    EventFilter.info(pattern=s"send ${header("tracking-nr")} back to broker at ${header("reply-to")}", occurrences=1) intercept {
      gate ! Consume(header, html.toString)
    }
  }

}
