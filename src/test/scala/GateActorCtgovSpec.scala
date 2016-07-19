package eu.themerius.docelemstore

import org.scalatest.{ BeforeAndAfterAll, FlatSpecLike, Matchers, Ignore }
import akka.actor.{ Props, ActorSystem }
import akka.testkit.{ ImplicitSender, TestKit, TestActorRef }
import scala.concurrent.duration._

import akka.testkit.EventFilter
import com.typesafe.config.ConfigFactory

import java.nio.file.{ Files, Paths }


@Ignore
class GateActorCtgovSpec(_system: ActorSystem)
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
    val uri = getClass.getResource("/sample-ctgov-NCT01463384-annot.xml").toURI
    val bytes = Files.readAllBytes(Paths.get(uri))
    new String(bytes, "UTF-8")
  }

  "An Gate Ctgov" should "be able to consume a gzipped XCAS" in {
    val gate = TestActorRef(Props[Gate])

    val header = Map(
      "content-type" -> "xmi",
      "event" -> "ExtractCtgovUseCase"
    )

    EventFilter.info(pattern="artifactsWriter", occurrences=1) intercept {
      gate ! Consume(header, getSampleXCAS)
    }

  }

}
