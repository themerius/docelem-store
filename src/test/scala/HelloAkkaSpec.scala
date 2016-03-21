package eu.themerius.docelemstore

import org.scalatest.{ BeforeAndAfterAll, FlatSpecLike, Matchers }
import akka.actor.{ Actor, Props, ActorSystem }
import akka.testkit.{ ImplicitSender, TestKit, TestActorRef }
import scala.concurrent.duration._

import java.nio.file.{ Files, Paths }

class HelloAkkaSpec(_system: ActorSystem)
  extends TestKit(_system)
  with ImplicitSender
  with Matchers
  with FlatSpecLike
  with BeforeAndAfterAll {

  def this() = this(ActorSystem("docelem-store-test"))

  override def afterAll: Unit = {
    system.shutdown()
    system.awaitTermination(10.seconds)
  }

  def getSampleXCAS = {
    val uri = getClass.getResource("/sample-gzip-xcas").toURI
    val bytes = Files.readAllBytes(Paths.get(uri))
    new String(bytes, "UTF-8")
  }

  "An Gate" should "be able to consume a gzipped XCAS" in {
    val gate = TestActorRef(Props[Gate])

    val header = Map(
      "content-type" -> "gzip-xml",
      "event" -> "ExtractNNEs"
    )

    gate ! Consume(header, getSampleXCAS)

    gate.underlyingActor.asInstanceOf[Gate].brokerUsr should be("admin")
  }

  // it should "be able to get a new greeting" in {
  //   val greeter = system.actorOf(Props[Greeter], "greeter")
  //   greeter ! WhoToGreet("testkit")
  //   greeter ! Greet
  //   expectMsgType[Greeting].message.toString should be("hello, testkit")
  // }
}
