package eu.themerius.docelemstore

import com.tinkerpop.blueprints.impls.orient.OrientGraph
import com.tinkerpop.blueprints.impls.orient.OrientGraphFactory
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.metadata.schema.OClass
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException

import scala.collection.JavaConverters._
import scala.collection.JavaConversions._

import java.security.MessageDigest;
import java.util.Base64;

import scala.xml.XML

import eu.themerius.docelemstore.utils.Stats.time

case class DocElemPayload(uuid: String, typ: String, model: String)


trait Database {
  def fetchDocElemPayload(uuid: String): DocElemPayload
  def saveDocElemPayloads(deps: Seq[DocElemPayload])
}


object OrientDB extends Database {

  new OrientGraph("plocal:/tmp/docelem-store")

  val factory = new OrientGraphFactory("plocal:/tmp/docelem-store").setupPool(1,100)
  def graph = factory.getTx

  def createDocElemSchema(name: String) = {
    val db = factory.getDatabase()
    val deClass = db.getMetadata().getSchema().getOrCreateClass(name)

    if (deClass.getProperty("uuid") == null) {
      val vClass = db.getMetadata().getSchema().getOrCreateClass("V")
      deClass.addSuperClass(vClass)

      db.commit()
      deClass.createProperty("uuid", OType.STRING)
      deClass.createIndex(s"${name}.uuid", OClass.INDEX_TYPE.NOTUNIQUE_HASH_INDEX, "uuid")
      deClass.createProperty("utc", OType.DATETIME)
      deClass.createProperty("model", OType.STRING)
      deClass.createIndex(s"${name}.model", "FULLTEXT", null, null, "LUCENE", List("model"): _*)
      deClass.createProperty("hash", OType.STRING)
      deClass.createIndex(s"${name}.hash", OClass.INDEX_TYPE.UNIQUE_HASH_INDEX, "hash")
      deClass.createProperty("prev", OType.STRING)

      //deClass.createIndex(s"${name}.hash", OClass.INDEX_TYPE.UNIQUE_HASH_INDEX, List("uuid", "hash"): _*)

      println("Created DocElem Type " + name)
    }
  }

  def modelHasher(salt: String, model: String): String = {
    val md = MessageDigest.getInstance("SHA-1")
    // All automatically imported models should have the same salt,
    // so that they can be deduplicated.
    // Manual added models are salted with the uuid for example.
    md.update(salt.getBytes("UTF-8"))
    md.update(model.getBytes("UTF-8"))
    val base = Base64.getUrlEncoder()
    base.encodeToString(md.digest()).subSequence(0, 27).toString()
  }

  def fetchDocElemPayload(uuid: String): DocElemPayload = {
    println(s"Fetching $uuid")
    val deVertex = graph.query.has("uuid", uuid).vertices.toList(0)
    val deModel = deVertex.getProperty("model").asInstanceOf[String]
    val deType = deVertex.getProperty("type").asInstanceOf[String]
    DocElemPayload(uuid, deType, deModel)
  }

  // do a batch import
  def saveDocElemPayloads(deps: Seq[DocElemPayload]) = time ("OrientDB:save") {
    // make new schemas for each found type
    val typs = deps.map(_.typ).toSet
    typs.map(createDocElemSchema)

    // push the data to db
    var duplicates = 0

    deps.foreach { de =>
      val g = graph

      g.addVertex(
        "class:" + de.typ,
        "uuid", de.uuid,
        "model", de.model,
        "hash", modelHasher("dump-import", de.model)
      )

      try {
        // note: all addVertex in a big transaction may be more performant
        g.commit()
      } catch {
        case e: ORecordDuplicatedException => {
          duplicates += 1
          g.rollback()
        }
      } finally {
        g.shutdown()
      }
    }

    println(s"Added ${deps.size} DocElems into the Database.")
    println(s"And ${duplicates} where duplicates, so they are ignored.")
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
  def getDocElemPayload = time ("XMLReader") {
    val nodes = xml \\ "modd" \\ "docelems" \\ "docelem"
    val payloads = nodes.map(
      d => DocElemPayload(d \ "id" text, d \ "type" text, (d \ "model")(0).child.mkString)
    )
    payloads
  }
}
