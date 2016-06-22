package eu.themerius.docelemstore


import org.scalatest._

import java.nio.file.{ Files, Paths }
import java.net.URI


class XCasCtgovSpec extends FlatSpec with Matchers {

  def getSampleXCas: Array[Byte] = {
    val uri = getClass.getResource("/sample-ctgov-NCT01463384-annot.xml").toURI
    Files.readAllBytes(Paths.get(uri))
  }

  "A XCas CTgov model" should "deserialize data to a CAS object" in {
    val casModel = new XCasModel
    casModel.deserialize(getSampleXCas)

    casModel.cas.getViewName() should equal ("_InitialView")
  }

  it should "extract the ctgov header" in {

    val casModel = new XCasModel with ExtractHeader {
      override def applyRules = {
        val headerArtifacts = genContentArtifacts(header)
        Corpus(headerArtifacts)
      }
    }

    casModel.deserialize(getSampleXCas)
    val corpus = casModel.applyRules

    corpus.artifacts.size should be (4)
    corpus.artifacts(0).sigmatics should equal (
      new URI ("header/clinicaltrials.gov:NCT01463384")
    )

  }

  it should "extract the content artifacts for sections and subsections" in {

    val casModel = new XCasModel with ExtractSections {
      override def applyRules = {
        val sectionsArtifacts = sections.map(genContentArtifacts).flatten
        val subSectionsArtifacts = subSections.map(genContentArtifacts).flatten
        Corpus(sectionsArtifacts ++ subSectionsArtifacts)
      }
    }

    casModel.deserialize(getSampleXCas)
    val corpus = casModel.applyRules

    // 2 artifacts per (sub)section
    corpus.artifacts.size should be (2*9)

    corpus.artifacts(0).sigmatics should equal (
      new URI ("section/name:overview+overview")
    )

    corpus.artifacts(0).semantics should equal (
      new URI ("section/title")
    )

    new String(corpus.artifacts(0).model) should include (
      "Overview"
    )

    corpus.artifacts(14).sigmatics should equal (
      new URI ("sub-section/name:inclusion_criteria+inclusion_criteria")
    )

    corpus.artifacts(14).semantics should equal (
      new URI ("section/title")
    )

    new String(corpus.artifacts(14).model) should include (
      "Inclusion Criteria."
    )

  }

  it should "extract the content artifacts from list document elements" in {

    val casModel = new XCasModel with ExtractLists {
      override def applyRules = {
        val listAritfacts = lists.map(genContentArtifact)
        Corpus(listAritfacts)
      }
    }

    casModel.deserialize(getSampleXCas)
    val corpus = casModel.applyRules

    corpus.artifacts.size should be (1)

    corpus.artifacts(0).sigmatics should equal (
      new URI ("list/murmur3:7c7fd3fe")
    )

    new String(corpus.artifacts(0).model) should include (
      "<li>9. Reference:</li>"
    )

  }

  it should "extract generic docelems in hiearchy" in {

    val casModel = new XCasModel with ExtractGenericHierarchy {
      override def applyRules = {
        val topologyArtifacts = hierarchizedDocelems.map(genTopologyArtifact)
        Corpus(topologyArtifacts)
      }
    }

    casModel.deserialize(getSampleXCas)
    val corpus = casModel.applyRules

    // header follows itself
    corpus.artifacts(0).semantics should equal (new URI("topo/header/clinicaltrials.gov:NCT01463384"))
    // section follows the header
    corpus.artifacts(6).semantics should equal (new URI("topo/header/clinicaltrials.gov:NCT01463384"))
    // this sub-section follows the first section
    corpus.artifacts(22).semantics should equal (new URI("topo/section/name:criteria+criteria"))
    // .. and so on ..

  }

  it should "extract also sentences as part of the hiearchy" in {

    val casModel = new XCasModel with ExtractGenericHierarchy with ExtractSentences {
      override def applyRules = {
        val topologyArtifacts = hierarchizedDocelems.map(genTopologyArtifact)
        val sentenceArtifacts = sentences.map(genContentArtifact)
        Corpus(topologyArtifacts ++ sentenceArtifacts)
      }
    }

    casModel.deserialize(getSampleXCas)
    val corpus = casModel.applyRules

    //corpus.artifacts.toList.map(println)

    // a sentence follows a paragraph
    corpus.artifacts(51).sigmatics.toString should equal ("sentence/murmur3:9305787a")
    corpus.artifacts(51).semantics.toString should equal ("topo/paragraph/murmur3:6067ffd8")

    // a sentence with content
    corpus.artifacts(100).sigmatics.toString should equal ("sentence/murmur3:2dfc030e")
    new String(corpus.artifacts(100).model) should equal ("- Cognitively normal elderly subjects between the ages of 55-90 and patients aged 55 - 90 years who have mild cognitive impairment (MCI) or clinically defined Alzheimer's disease.")

  }

}
