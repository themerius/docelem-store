package eu.themerius.docelemstore


import org.scalatest._

import java.nio.file.{ Files, Paths }
import java.net.URI


class XCasModelSpec extends FlatSpec with Matchers {

  def getSampleGzippedXCas: Array[Byte] = {
    val uri = getClass.getResource("/sample-gzip-xcas").toURI
    Files.readAllBytes(Paths.get(uri))
  }

  "A XCasModel" should "deserialize data to a CAS object" in {
    val casModel = new GzippedXCasModel
    casModel.deserialize(getSampleGzippedXCas)

    casModel.cas.getViewName() should equal ("_InitialView")
  }

  it should "extract NNEs as Corpus when mixing-in the ExtractNNEs rule" in {
    val casModel = new GzippedXCasModel with ExtractNNEs
    casModel.deserialize(getSampleGzippedXCas)
    val corpus = casModel.applyRules

    corpus.artifacts.size should be > 40
    corpus.artifacts(0).sigmatics should equal (new URI("scaiview.header/pmid:161461"))
    corpus.artifacts(0).pragmatics should equal (new URI("run/name:scaiview-run-1"))
    new String(corpus.artifacts(0).model) should include ("pos")
  }
}