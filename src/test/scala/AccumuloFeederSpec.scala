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

  "I2B Relation Marginal Note Example" should "have a scaiview.abstract and some meta data" in {

    val af = TestActorRef(Props[AccumuloFeeder])
    var artifacts = Seq[KnowledgeArtifact]()

    // Note: scaiview.abstract means title tab tab text
    artifacts = KnowledgeArtifact(
      new URI("header/pmid:25690317"),
      new URI("_"),
      new URI("header/header"),
      "Investigating the genetic basis of fever-associated syndromic epilepsies using copy number variation analysis.\t\tFever-associated syndromic epilepsies ranging from febrile seizures plus (FS+) to Dravet syndrome have a significant genetic component. However, apart from SCN1A mutations in >80% of patients with Dravet syndrome, the genetic underpinnings of these epilepsies remain largely unknown. Therefore, we performed a genome-wide screening for copy number variations (CNVs) in 36 patients with SCN1A-negative fever-associated syndromic epilepsies. Phenotypes included Dravet syndrome (n = 23; 64%), genetic epilepsy with febrile seizures plus (GEFS+) and febrile seizures plus (FS+) (n = 11; 31%) and unclassified fever-associated epilepsies (n = 2; 6%). Array comparative genomic hybridization (CGH) was performed using Agilent 4 × 180K arrays. We identified 13 rare CNVs in 8 (22%) of 36 individuals. These included known pathogenic CNVs in 4 (11%) of 36 patients: a 1q21.1 duplication in a proband with Dravet syndrome, a 14q23.3 deletion in a proband with FS+, and two deletions at 16p11.2 and 1q44 in two individuals with fever-associated epilepsy with concomitant autism and/or intellectual disability. In addition, a 3q13.11 duplication in a patient with FS+ and two de novo duplications at 7p14.2 and 18q12.2 in a patient with atypical Dravet syndrome were classified as likely pathogenic. Six CNVs were of unknown significance. The identified genomic aberrations overlap with known neurodevelopmental disorders, suggesting that fever-associated epilepsy syndromes may be a recurrent clinical presentation of known microdeletion syndromes.".stripMargin.getBytes,
      Meta(new URI("scaiview.abstract"), 1)
    ) +: artifacts

    artifacts = KnowledgeArtifact(
      new URI("header/pmid:25690317"),
      new URI("_"),
      new URI("header/authors"),
      """Hartmann et al.""".stripMargin.getBytes,
      Meta(new URI("freetext"), 1)
    ) +: artifacts

    artifacts = KnowledgeArtifact(
      new URI("header/pmid:25690317"),
      new URI("_"),
      new URI("header/publicationDate"),
      """2015-03""".stripMargin.getBytes,
      Meta(new URI("freetext"), 1)
    ) +: artifacts

    //af ! DocElem2Accumulo(Corpus(artifacts))

  }

  it should "have some tiny BEL relation documents as marginal notes" in {

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

    //af ! DocElem2Accumulo(Corpus(artifacts))

  }

  it should "have annotations with position vectors for the highlighting" in {

    val af = TestActorRef(Props[AccumuloFeeder])
    var artifacts = Seq[KnowledgeArtifact]()

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

    //af ! DocElem2Accumulo(Corpus(artifacts))

  }

  it should "have annotations for helping the big-table search/scan/seek" in {

    pending

    val af = TestActorRef(Props[AccumuloFeeder])
    var artifacts = Seq[KnowledgeArtifact]()

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

    //af ! DocElem2Accumulo(Corpus(artifacts))

  }

}
