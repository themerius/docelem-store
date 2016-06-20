package eu.themerius.docelemstore


import org.scalatest._

import java.nio.file.{ Files, Paths }
import java.net.URI


class XCasCtgovSpec extends FlatSpec with Matchers {

  def getSampleXCas: Array[Byte] = {
    val uri = getClass.getResource("/sample-ctgov2.xml").toURI
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
      new URI ("header/clinicaltrials.gov:NCT00000171")
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
    corpus.artifacts.size should be (2*8)

    corpus.artifacts(0).sigmatics should equal (
      new URI ("section/overview+overview")
    )

    corpus.artifacts(0).semantics should equal (
      new URI ("section/title")
    )

    new String(corpus.artifacts(0).model) should include (
      "Overview"
    )

    corpus.artifacts(12).sigmatics should equal (
      new URI ("sub-section/inclusion_criteria+inclusion_criteria")
    )

    corpus.artifacts(12).semantics should equal (
      new URI ("section/title")
    )

    new String(corpus.artifacts(12).model) should include (
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
      new URI ("list/murmur3:157dac74")
    )

    new String(corpus.artifacts(0).model) should include (
      "2.1 Intervention Type:	Drug"
    )

  }

  it should "extract generic docelems in hiearchy" in {

    val casModel = new XCasModel with ExtractGenericDocElemHierarchy {
      override def applyRules = {
        val artifacts = hierarchizedDocelems.map(genTopologyArtifact)
        Corpus(artifacts)
      }
    }

    casModel.deserialize(getSampleXCas)
    val corpus = casModel.applyRules

    // header follows itself
    corpus.artifacts(0).semantics should equal (new URI("topo/header/clinicaltrials.gov:NCT00000171"))
    // section follows the header
    corpus.artifacts(7).semantics should equal (new URI("topo/header/clinicaltrials.gov:NCT00000171"))
    // this sub-section follows the first section
    corpus.artifacts(8).semantics should equal (new URI("topo/section/name:criteria+criteria"))
    // .. and so on ..

  }

}
