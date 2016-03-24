package eu.themerius.docelemstore

import java.net.URI
import scala.xml.Node

case class Transform2DocElem(model: ModelTransRules, data: Array[Byte])

// TODO: do we need a special constrainted/extended URI?
// The @, :, !, ...
case class KnowledgeArtifact(
  sigmatics: URI,  // indication of a information.
  pragmatics: URI, // the annotation layer.
  semantics: URI,  // represents a attribute of the doc elem.
  model: Array[Byte],  // the raw (domain) model (or signal) as byte array.
  meta: Meta
)

case class Meta(
  specification: URI,  // link to the model's specification document.
  fingerprint: Int = 0,
  timestamp: Long = System.currentTimeMillis
)

// A sequence of KnowledgeArtifacts with the same sigmatics build up a whole DocElem.
// DocElem Core is build up only with the null-layer.
// DocElem Shell is build up with all other layers (this are esp. the classic "annotations" or the hierarchial/topological information)

// A sequence of DocElems or any KnowledgeArtifacts is a Corpus.
case class Corpus(artifacts: Seq[KnowledgeArtifact])

object QueryTarget extends Enumeration {
  val SingleDocElem, DocumentTopology, SemanticSearch = Value
}

case class Query(target: QueryTarget.Value, xml: Node)
