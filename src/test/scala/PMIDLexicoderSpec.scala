package eu.themerius.docelemstore.utils


import org.scalatest._


class PMIDLexicoderSpec extends FlatSpec with Matchers {

  val pl = new PMIDLexicoder

  "The PMIDLexicoder" should "encode PMID labels with trailing zeros" in {
    new String(pl.encode("PMID:1")) should equal ("PMID:00000001")
  }

  it should "also encode if it has already trainling zeros" in {
    new String(pl.encode("PMID:00000001")) should equal ("PMID:00000001")
  }

  it should "decode a byte array back to PMID labels" in {
    pl.decode("PMID:00000001".getBytes) should equal ("PMID:1")
  }

  it should "also encode PMCID labels with trailing zeros" in {
    new String(pl.encode("PMCID:PMC3918480")) should equal ("PMCID:PMC03918480")
  }

  it should "decode a byte array back to PMCID labels" in {
    pl.decode("PMCID:PMC03918480".getBytes) should equal ("PMCID:PMC3918480")
  }

  it should "only encode PMID and PMCID labels" in {
    new String(pl.encode("BLA:1")) should equal ("BLA:1")
  }

}
