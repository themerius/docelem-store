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
      new URI("header/pmid:25690317"),
      new URI("belief/run:test"),
      new URI("bel/document:1"),
      """SET MeSHDisease = "Epilepsy"
        |path(MESHD:"Epilepsies, Myoclonic") -- path(MESHD:Fever)
        |path(MESHD:Seizures) -- path(MESHD:Fever)""".stripMargin.getBytes,
      Meta(new URI("bel@v1.0"), 1)
    ) +: artifacts

    artifacts = KnowledgeArtifact(
      new URI("header/pmid:25690317"),
      new URI("belief/run:test"),
      new URI("bel/document:2"),
      """SET MeSHDisease = "Epilepsy"
        |SET Species = "9606"
        |path(MESHD:Epilepsy) -- p(HGNC:SCN1A)""".stripMargin.getBytes,
      Meta(new URI("bel@v1.0"), 2)
    ) +: artifacts

    artifacts = KnowledgeArtifact(
      new URI("header/pmid:25690317"),
      new URI("belief/run:test"),
      new URI("bel/document:3"),
      """SET MeSHDisease = "Epilepsy"
        |path(MESHD:"Intellectual Disability") -- path(MESHD:Fever)
        |path(MESHD:Epilepsy) -- path(MESHD:Fever)""".stripMargin.getBytes,
      Meta(new URI("bel@v1.0"), 3)
    ) +: artifacts

    // annotations for highlighting annotations "position vectors"

    artifacts = KnowledgeArtifact(
      new URI("header/pmid:25690317"),
      new URI("belief/run:test"),
      new URI("bel/document:1"),
      """{"pos": [
        {"begin": 194, "end": 209, "attr": "header/header", "ref": "concept/mesh_disease:epilepsies_myoclonic"},
        {"begin": 112, "end": 117, "attr": "header/header", "ref": "concept/meddra:chills_&amp;_fever"},
        {"begin": 118, "end": 128, "attr": "header/header", "ref": "concept/vph:associated_with"},
        {"begin": 163, "end": 179, "attr": "header/header", "ref": "concept/mesh_disease:seizures_febrile"} ]}""".stripMargin.getBytes,
      Meta(new URI("annotation@v1"), 100)
    ) +: artifacts

    artifacts = KnowledgeArtifact(
      new URI("header/pmid:25690317"),
      new URI("belief/run:test"),
      new URI("bel/document:2"),
      """{"pos": [
        {"begin": 540, "end": 550, "attr": "header/header", "ref": "concept/mesh_disease:epilepsy"},
        {"begin": 498, "end": 503, "attr": "header/header", "ref": "concept/homo_sapiens:scn1a"}
        ]}""".stripMargin.getBytes,
      Meta(new URI("annotation@v1"), 200)
    ) +: artifacts

    artifacts = KnowledgeArtifact(
      new URI("header/pmid:25690317"),
      new URI("belief/run:test"),
      new URI("bel/document:3"),
      """{"pos": [
        {"begin": 1188, "end": 1211, "attr": "header/header", "ref": "concept/mesh_disease:intellectual_disability"},
        {"begin": 1148, "end": 1156, "attr": "header/header", "ref": "concept/mesh_disease:epilepsy"},
        {"begin": 1131, "end": 1136, "attr": "header/header", "ref": "concept/mesh_disease:fever"},
        {"begin": 1137, "end": 1147, "attr": "header/header", "ref": "concept/vph:associated_with"} ]}""".stripMargin.getBytes,
      Meta(new URI("annotation@v1"), 300)
    ) +: artifacts

    // TODO: Only for the search

    // artifacts = KnowledgeArtifact(
    //   new URI("header/pmid:25690317"),
    //   new URI("belief/run:test/spo"),
    //   new URI("bel/s/MESHD:Asthma"),
    //   "".stripMargin.getBytes,
    //   Meta(new URI("freetext"))
    // ) +: artifacts
    //
    // artifacts = KnowledgeArtifact(
    //   new URI("header/pmid:25690317"),
    //   new URI("belief/run:test/spo"),
    //   new URI("bel/p/associated"),
    //   "".stripMargin.getBytes,
    //   Meta(new URI("freetext"))
    // ) +: artifacts
    //
    // artifacts = KnowledgeArtifact(
    //   new URI("header/pmid:25690317"),
    //   new URI("belief/run:test/spo"),
    //   new URI("bel/o/CHEBI:bronchodilator_agent"),
    //   "".stripMargin.getBytes,
    //   Meta(new URI("freetext"))
    // ) +: artifacts
    //
    // artifacts = KnowledgeArtifact(
    //   new URI("header/pmid:25690317"),
    //   new URI("belief/run:test/spo"),
    //   new URI("bel/s/MESHD:Asthma/p/associated"), // analogous: p-o and s-o
    //   "".stripMargin.getBytes,
    //   Meta(new URI("freetext"))
    // ) +: artifacts

    af ! DocElem2Accumulo(Corpus(artifacts))
  }

}
