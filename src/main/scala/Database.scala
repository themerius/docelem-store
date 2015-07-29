package eu.themerius.docelemstore

import com.tinkerpop.blueprints.impls.orient.OrientGraph
import com.tinkerpop.blueprints.impls.orient.OrientGraphFactory
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.metadata.schema.OClass
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException
import com.orientechnologies.orient.core.exception.OConcurrentModificationException

import com.orientechnologies.orient.jdbc.OrientJdbcDriver

import scala.collection.JavaConverters._
import scala.collection.JavaConversions._

import java.security.MessageDigest
import java.util.Base64
import java.util.Properties

import scala.xml.XML

import eu.themerius.docelemstore.utils.Stats.time

case class DocElemPayload(uuid: String, typ: String, model: String)

object Annotation {
  case class ConnectedVs(fromUUID: String, toUUID: String)
  case class Semantics(purpose: String, layer: String, position: Map[String, Any])
}

import Annotation._


trait Database {
  def fetchDocElemPayload(uuid: String): DocElemPayload
  def saveDocElemPayloads(deps: Seq[DocElemPayload])
}


object OrientDB extends Database {

  new OrientGraph("plocal:/tmp/docelem-store")

  val factory = new OrientGraphFactory("plocal:/tmp/docelem-store").setupPool(10,100)
  def graph = factory.getTx

  // Init JDBC
  val info = new Properties;
  info.put("db.usePool", "true")
  info.put("db.pool.min", "10")
  info.put("db.pool.max", "100")
  Class.forName("com.orientechnologies.orient.jdbc.OrientJdbcDriver")
  val jdbc = java.sql.DriverManager.getConnection("jdbc:orient:plocal:/tmp/docelem-store", info)

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

  def createAnnotationSchema(name: String) = {
    val db = factory.getDatabase()
    val deClass = db.getMetadata().getSchema().getOrCreateClass(name)

    if (deClass.getProperty("layer") == null) {
      val vClass = db.getMetadata().getSchema().getOrCreateClass("E")
      deClass.addSuperClass(vClass)

      db.commit()
      deClass.createProperty("layer", OType.STRING)
      deClass.createIndex(s"${name}.layer", OClass.INDEX_TYPE.NOTUNIQUE_HASH_INDEX, "layer")
      deClass.createProperty("hash", OType.STRING)
      deClass.createIndex(s"${name}.hash", OClass.INDEX_TYPE.UNIQUE_HASH_INDEX, "hash")

      println("Created Annotation Type " + name)
    }
  }

  def hasher(salts: List[String], model: String): String = {
    val md = MessageDigest.getInstance("SHA-1")
    // All automatically imported models should have the same salt,
    // so that they can be deduplicated.
    // Manual added models are salted with the uuid for example.
    salts.foreach( salt => md.update(salt.getBytes("UTF-8")) )
    md.update(model.getBytes("UTF-8"))
    val base = Base64.getUrlEncoder()
    base.encodeToString(md.digest()).subSequence(0, 27).toString()
  }

  def fetchDocElemPayload(uuid: String): DocElemPayload = time (s"OrientDB:fetch:$uuid") {
    val stmt = jdbc.createStatement()
    val rs = stmt.executeQuery(s"""
      select from V where uuid = "$uuid" order by @rid desc skip 0 limit 1
    """)
    // fetch the first record
    rs.next()
    val model = rs.getString("model")
    val typ = rs.getString("@class")
    rs.close()
    stmt.close()
    DocElemPayload(uuid, typ, model)
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
        "hash", hasher(List("dump-import"), de.model)
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

  def annotate(ids: ConnectedVs, sem: Semantics, otherProps: Map[String, Any]): Unit =
    time ("OrientDB:annotate") {
      createAnnotationSchema(sem.purpose)

      val stmt = jdbc.createStatement()
      val rs1 = stmt.executeQuery(s"""
        select from V where uuid = "${ids.fromUUID}" order by @rid desc skip 0 limit 1
      """)
      val rs2 = stmt.executeQuery(s"""
        select from V where uuid = "${ids.toUUID}" order by @rid desc skip 0 limit 1
      """)
      // fetch the first record
      rs1.next()
      val rid1 = rs1.getString("@rid")
      rs2.next()
      val rid2 = rs2.getString("@rid")
      rs1.close()
      rs2.close()
      stmt.close()

      val g = graph
      try {
        val v1 = g.getVertex(rid1)
        val v2 = g.getVertex(rid2)
        val props = Map(
          "layer" -> sem.layer,
          "hash" -> hasher(
            List(ids.fromUUID, ids.toUUID, sem.purpose, sem.layer),
            v1.getProperty("hash")) // for deduplication on version (hash) and layer
        )
        val edge = g.addEdge(null, v1, v2, sem.purpose)
        edge.setProperties(mapAsJavaMap(props ++ otherProps))
        g.commit()
        println("Created " + edge)
      } catch {
        case e: OConcurrentModificationException => {
          g.rollback()
          println(s"ROLLBACK ${ids.fromUUID} -${sem.purpose}-> ${ids.toUUID}!" +
                   " Because we are optimistic. Retry...")
          // Retrying because on the same vertex may also be annotated at the same time.
          annotate(ids, sem, otherProps)
        }
        case e: ORecordDuplicatedException => {
          g.rollback()
          println(s"ROLLBACK duplicated ${ids.fromUUID} -${sem.purpose}-> ${ids.toUUID}!")
        }
      } finally {
        g.shutdown()
      }
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
