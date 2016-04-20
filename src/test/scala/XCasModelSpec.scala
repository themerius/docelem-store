package eu.themerius.docelemstore


import org.scalatest._

import java.nio.file.{ Files, Paths }
import java.net.URI


class XCasModelSpec extends FlatSpec with Matchers {

  def getSampleGzippedXCas: Array[Byte] = {
    val uri = getClass.getResource("/sample-epilepsy-gzip-xcas").toURI
    Files.readAllBytes(Paths.get(uri))
  }

  def getSampleGzippedXCasRelations: Array[Byte] = {
    val uri = getClass.getResource("/sample-relations-gzip-xcas").toURI
    Files.readAllBytes(Paths.get(uri))
  }

  "A XCasModel" should "deserialize data to a CAS object" in {
    val casModel = new GzippedXCasModel
    casModel.deserialize(getSampleGzippedXCas)

    casModel.cas.getViewName() should equal ("_InitialView")
  }

  it should "extract paragraphs as corpus containing the topology" in {

    val casModel = new GzippedXCasModel with ExtractParagraphs {
      override def applyRules = {
        val topo = paragraphs.zipWithIndex
          .map(t => genTopologyArtifact(t._1, t._2))
        Corpus(topo)
      }
    }

    casModel.deserialize(getSampleGzippedXCasRelations)
    val corpus = casModel.applyRules

    corpus.artifacts.size should be (1)
    corpus.artifacts(0).sigmatics should equal (
      new URI ("paragraph/murmur3:972fdd49")
    )
    corpus.artifacts(0).pragmatics should equal (
      new URI ("header/pmid:11358825")
    )
    corpus.artifacts(0).semantics should equal (
      new URI ("topo/header/pmid:11358825")
    )
    new String(corpus.artifacts(0).model) should include (
      "128"
    )

  }

  it should "extract sentences as corpus containing the content" in {

    val casModel = new GzippedXCasModel with ExtractSentences {
      override def applyRules = {
        val content = sentences.map(genContentArtifact)
        Corpus(content)
      }
    }

    casModel.deserialize(getSampleGzippedXCasRelations)
    val corpus = casModel.applyRules

    corpus.artifacts.size should be (9)
    corpus.artifacts(0).sigmatics should equal (
      new URI ("sentence/murmur3:9ff418a")
    )
    corpus.artifacts(0).pragmatics should equal (
      new URI ("_")
    )
    corpus.artifacts(0).semantics should equal (
      new URI ("sentence/sentence")
    )
    new String(corpus.artifacts(0).model) should include (
      "polymorphonuclear cell-mediated tumor"
    )

  }

  it should "extract sentences from paragraphs as corpus containing the topology" in {

    val casModel = new GzippedXCasModel with ExtractParagraphs with ExtractSentences {
      override def applyRules = {
        val topo = paragraphs.map(sentences).flatten.zipWithIndex.map(t => genTopologyArtifact(t._1._1, t._1._2, t._2))
        Corpus(topo)
      }
    }

    casModel.deserialize(getSampleGzippedXCasRelations)
    val corpus = casModel.applyRules

    corpus.artifacts.size should be (9)
    corpus.artifacts(0).sigmatics should equal (
      new URI ("sentence/murmur3:9ff418a")
    )
    corpus.artifacts(0).pragmatics should equal (
      new URI ("header/pmid:11358825")
    )
    corpus.artifacts(0).semantics should equal (
      new URI ("topo/paragraph/murmur3:972fdd49")
    )
    new String(corpus.artifacts(0).model) should include (
      "128"
    )

  }

  it should "extract nnes from sentences as corpus containing the annotations" in {

    val casModel = new GzippedXCasModel with ExtractSentences with ExtractNNEs {
      override def applyRules = {
        val annots = sentences.map(nnes).flatten
          .map(t => genAnnotationArtifact(t._1, t._2))
        Corpus(annots)
      }
    }

    casModel.deserialize(getSampleGzippedXCasRelations)
    val corpus = casModel.applyRules

    corpus.artifacts.size should be (56)
    corpus.artifacts(0).sigmatics should equal (
      new URI ("sentence/murmur3:9ff418a")
    )
    corpus.artifacts(0).pragmatics should equal (
      new URI ("MZ")
    )
    corpus.artifacts(0).semantics should equal (
      new URI ("concept/mesh_disease:neoplasms")
    )
    new String(corpus.artifacts(0).model) should include (
      "begin"
    )
    new String(corpus.artifacts(0).model) should include (
      "end"
    )

  }

  it should "extract relations from sentences as corpus containing the graph search artifacts" in {

    val casModel = new GzippedXCasModel with ExtractSentences with ExtractRelations {
      override def applyRules = {
        val searchArti = sentences.map(relations).flatten
          .map(t => genSearchArtifact(t._1, t._2)).flatten
        Corpus(searchArti)
      }
    }

    casModel.deserialize(getSampleGzippedXCasRelations)
    val corpus = casModel.applyRules

    corpus.artifacts.size should be (3)
    corpus.artifacts(0).sigmatics should equal (
      new URI ("sentence/murmur3:d25eb34c")
    )
    corpus.artifacts(0).pragmatics should equal (
      new URI ("belief/MZ")
    )
    corpus.artifacts(0).semantics should equal (
      new URI ("graph/annotation/mesh_disease:neoplasms")
    )

  }

  it should "extract relations from sentences as corpus containing the view artifacts" in {

    val casModel = new GzippedXCasModel with ExtractSentences with ExtractRelations {
      override def applyRules = {
        val viewArti = sentences.map(relations).flatten.zipWithIndex
          .map(t => genViewArtifacts(t._1._1, t._1._2, t._2))
        Corpus(viewArti)
      }
    }

    casModel.deserialize(getSampleGzippedXCasRelations)
    val corpus = casModel.applyRules

    corpus.artifacts.size should be (1)
    corpus.artifacts(0).sigmatics should equal (
      new URI ("sentence/murmur3:d25eb34c")
    )
    corpus.artifacts(0).pragmatics should equal (
      new URI ("belief/MZ")
    )
    corpus.artifacts(0).semantics should equal (
      new URI ("bel/document:0")
    )
    new String(corpus.artifacts(0).model) should include (
      "begin"
    )
    new String(corpus.artifacts(0).model) should include (
      "end"
    )

  }

  it should "extract relations from sentences as corpus containing the content" in {

    val casModel = new GzippedXCasModel with ExtractSentences with ExtractRelations {
      override def applyRules = {
        val content = sentences.map(relations).flatten.zipWithIndex
          .map(t => genContentArtifacts(t._1._1, t._1._2, t._2))
        Corpus(content)
      }
    }

    casModel.deserialize(getSampleGzippedXCasRelations)
    val corpus = casModel.applyRules

    corpus.artifacts.size should be (1)
    corpus.artifacts(0).sigmatics should equal (
      new URI ("sentence/murmur3:d25eb34c")
    )
    corpus.artifacts(0).pragmatics should equal (
      new URI ("belief/MZ")
    )
    corpus.artifacts(0).semantics should equal (
      new URI ("bel/document:0")
    )
    new String(corpus.artifacts(0).model) should include (
      "path(MESHD:Neoplasms) -- p(MGI:Fcgr1)"
    )

  }

}
