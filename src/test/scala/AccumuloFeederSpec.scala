package eu.themerius.docelemstore

import org.scalatest.{ BeforeAndAfterAll, FlatSpecLike, Matchers }
import akka.actor.{ Props, ActorSystem }
import akka.testkit.{ ImplicitSender, TestKit, TestActorRef }
import scala.concurrent.duration._

import akka.testkit.EventFilter
import com.typesafe.config.ConfigFactory

import java.nio.file.{ Files, Paths }
import java.net.URI

class AccumuloFeederSpec(_system: ActorSystem)
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

  "An AccumuloFeeder" should "be able write a (BEL) Cropus into Accumulo" in {
    val af = TestActorRef(Props[AccumuloFeeder])

    var artifacts = Seq[KnowledgeArtifact]()

    artifacts = KnowledgeArtifact(
      new URI("header/pmid:161461"),
      new URI("belief/run:unit-test"),
      new URI("bel/document:1"),
      """SET MeSHDisease = "Asthma"
path(MESHD:Asthma) -- a(CHEBI:"bronchodilator agent")
path(MESHD:Asthma) -- p(HGNC:ADRB2)
a(CHEMBLID:CHEMBL356716) -- p(HGNC:ADRB2)""".getBytes,
      Meta(new URI("bel@v1.0"), 1)
    ) +: artifacts

    artifacts = KnowledgeArtifact(
      new URI("header/pmid:161461"),
      new URI("belief/run:unit-test"),
      new URI("bel/document:2"),
      """a(SCHEM:Salbutamol) -- a(CHEMBLID:CHEMBL356716)
a(CHEMBLID:CHEMBL434) -- a(SCHEM:Clenbuterol)""".getBytes,
      Meta(new URI("bel@v1.0"), 2)
    ) +: artifacts

    // annotations for highlighting annotations "position vectors"

    artifacts = KnowledgeArtifact(
      new URI("header/pmid:161461"),
      new URI("belief/run:unit-test"),
      new URI("bel/document:1"),
      """{"pos": [
        {"begin": 237, "end": 253, "attr": "header/header", "ref": "concept/meddra:allergic_asthma"},
        {"begin": 335, "end": 345, "attr": "header/header", "ref": "concept/mergedbiomarker:resistence"},
        {"begin": 124, "end": 131, "attr": "header/header", "ref": "concept/plio:agonist"} ]}""".getBytes,
      Meta(new URI("annotation@v1"), 100)
    ) +: artifacts

    artifacts = KnowledgeArtifact(
      new URI("header/pmid:161461"),
      new URI("belief/run:unit-test"),
      new URI("bel/document:2"),
      """{"pos": [
        {"begin": 473, "end": 481, "attr": "header/header", "ref": "concept/vph:agonist"} ]}""".getBytes,
      Meta(new URI("annotation@v1"), 200)
    ) +: artifacts

    // Only for the search

    artifacts = KnowledgeArtifact(
      new URI("header/pmid:161461"),
      new URI("belief/run:unit-test/spo"),
      new URI("bel/s/MESHD:Asthma"),
      "".getBytes,
      Meta(new URI("freetext"))
    ) +: artifacts

    artifacts = KnowledgeArtifact(
      new URI("header/pmid:161461"),
      new URI("belief/run:unit-test/spo"),
      new URI("bel/p/associated"),
      "".getBytes,
      Meta(new URI("freetext"))
    ) +: artifacts

    artifacts = KnowledgeArtifact(
      new URI("header/pmid:161461"),
      new URI("belief/run:unit-test/spo"),
      new URI("bel/o/CHEBI:bronchodilator_agent"),
      "".getBytes,
      Meta(new URI("freetext"))
    ) +: artifacts

    artifacts = KnowledgeArtifact(
      new URI("header/pmid:161461"),
      new URI("belief/run:unit-test/spo"),
      new URI("bel/s/MESHD:Asthma/p/associated"), // analogous: p-o and s-o
      "".getBytes,
      Meta(new URI("freetext"))
    ) +: artifacts

    af ! DocElem2Accumulo(Corpus(artifacts))
  }

}
