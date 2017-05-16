package eu.themerius.docelem.utils

import java.net.URI
import scala.xml.Node

case class DocElemURI(docElemType: String, idType: String, id: String) {
  def toURI: URI = new URI("")
}

case class SemanticDigitalAssetKey (
  sigmatics: URI,
  pragmatics: URI, // the annotation layer.
  semantics: URI,  // represents a attribute of the doc elem.
  syntax: URI,  // link to the model's specification document.
  fingerprint: Int = 0
)

case class SemanticDigitalAsset (
  sigmatics: URI,
  pragmatics: URI, // the annotation layer.
  semantics: URI,  // represents a attribute of the doc elem.
  syntax: URI,  // link to the model's specification document.
  fingerprint: Int = 0,
  timestamp: Long = System.currentTimeMillis,
  signal: Iterable[Byte]
)

case class Corpus(
  root: URI,  // the root/superordinate docelem id
  assets: Iterable[SemanticDigitalAsset]
)



/* Ein Corpus von SDAs die alle die gleiche Sigmatik haben, sind Dokument Elemente.
Ein Corpus von Dokument Elementen ist ein oder mehrere Dokumente.
*/
