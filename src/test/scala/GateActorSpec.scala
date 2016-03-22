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

  // it should "be able to get a new greeting" in {
  //   val greeter = system.actorOf(Props[Greeter], "greeter")
  //   greeter ! WhoToGreet("testkit")
  //   greeter ! Greet
  //   expectMsgType[Greeting].message.toString should be("hello, testkit")
  // }
}
