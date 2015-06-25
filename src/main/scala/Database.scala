package eu.themerius.docelemstore

import com.tinkerpop.blueprints.impls.orient.OrientGraph
import com.tinkerpop.blueprints.impls.orient.OrientGraphFactory

import scala.collection.JavaConverters._
import scala.collection.JavaConversions._

import scala.xml.XML

case class DocElemPayload(uuid: String, typ: String, model: String)


trait Database {
  def fetchDocElemPayload(uuid: String): DocElemPayload
  def saveDocElemPayloads(deps: Seq[DocElemPayload])
}


object OrientDB extends Database {

  new OrientGraph("plocal:/tmp/docelem-store")

  val factory = new OrientGraphFactory("plocal:/tmp/docelem-store").setupPool(1,10)
  def graph = factory.getTx

  def fetchDocElemPayload(uuid: String): DocElemPayload = {
    val deVertex = graph.query.has("uuid", uuid).vertices.toList(0)
    val deModel = deVertex.getProperty("model").asInstanceOf[String]
    val deType = deVertex.getProperty("type").asInstanceOf[String]
    DocElemPayload(uuid, deType, deModel)
  }

  // do a batch import
  def saveDocElemPayloads(deps: Seq[DocElemPayload]) = {
    val g = graph
    deps.map { de =>
      g.addVertex(
        "class:DocElem",
        "uuid", de.uuid,
        "type", de.typ,
        "model", de.model
      )
    }
    g.commit()
  }

}

class XMLReader(file: String) {
  val xml = XML.load(file)
  def getDocElemPayload = {
    val nodes = xml \\ "modd" \\ "docelems" \\ "docelem"
    nodes.map(
      d => DocElemPayload(d \ "id" text, d \ "type" text, (d \ "model")(0).child.mkString)
    )
  }
}
