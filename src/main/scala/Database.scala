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
    println(s"Fetching $uuid")
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
    println(s"Written ${deps.size} DocElems into the Database.")
    g.commit()
  }

  // uuidA is annotated by uuidB as Annotation.
  // TODO: add polymorphic method with generic position argument
  def annotatedWith(uuidA: String, uuidB: String): Unit = {
    val g = graph
    try {
      val va = g.query.has("uuid", uuidA).vertices.toList(0)
      val vb = g.query.has("uuid", uuidB).vertices.toList(0)
      val aAnnotatedB = graph.addEdge(null, va, vb, "annotated_with");
      g.commit()
      println(s"Created Edge(annotated_with) ${aAnnotatedB}.")
    } catch {
      case e: Exception => {
        g.rollback()
        println(s"ROLLBACK Edge(annotated_with) ${uuidA} -> ${uuidB}! Retry...")
        annotatedWith(uuidA, uuidB)
      }
    } finally {
      g.shutdown()
    }
  }

  def hasProvanance(uuidA: String, uuidB: String) = {
    val g = graph
    val va = g.query.has("uuid", uuidA).vertices.toList(0)
    val vb = g.query.has("uuid", uuidB).vertices.toList(0)
    val aAnnotatedB = graph.addEdge(null, va, vb, "has_provanance");
    println(s"Created Edge(has_provanance) ${aAnnotatedB}.")
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
