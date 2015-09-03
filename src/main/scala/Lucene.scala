package eu.themerius.docelemstore

import eu.themerius.docelemstore.utils.Stats.time

import org.apache.lucene.analysis.standard._
import org.apache.lucene.analysis.core.SimpleAnalyzer
import org.apache.lucene.store._
import org.apache.lucene.index._
import org.apache.lucene.document._
import org.apache.lucene.search._
import org.apache.lucene.queryparser.classic._

object LuceneTry extends App {

  //val analyzer = new StandardAnalyzer
  val analyzer = new SimpleAnalyzer
  //val directory = new RAMDirectory
  val directory = FSDirectory.open( (new java.io.File("/tmp/testindex")).toPath )
  val config = new IndexWriterConfig(analyzer)
  val iwriter = new IndexWriter(directory, config)

  for (x <- List.fill(5)(1)) time("lucene add") {
    val doc = new Document()
    doc.add(new Field("id", "hiho", Field.Store.YES, Field.Index.NOT_ANALYZED))
    doc.add(new Field("fieldname", "Hello World", TextField.TYPE_STORED))
    // alle attribute als nicht-indexiertes field. value ist ein json
    // oder soll ein extra index mit attributen entstehen? triple store oder kv store?
    iwriter.updateDocument(new Term("id", "hiho"), doc)  // no duplicates
  }
  iwriter.commit // write to index
  iwriter.close  // release write lock

  var mm = collection.mutable.HashMap[String, String]()
  for (x <- List.fill(5)(1)) time("map add") {
    mm("fieldname") = "Hello world"
  }

  val ireader = DirectoryReader.open(directory)
  val isearcher = new IndexSearcher(ireader)
  val parser = new QueryParser("fieldname", analyzer)
  val query = parser.parse("world")
  val hits = isearcher.search(query, null, 1000).scoreDocs
  hits.foreach { hit =>
    println(hit)
    val hitDoc = isearcher.doc(hit.doc)
    println(hitDoc.get("fieldname"))
  }

  ireader.close
  directory.close

}

object Lucene extends Database {
  val analyzer = new SimpleAnalyzer
  //val directory = new RAMDirectory
  val directory = FSDirectory.open( (new java.io.File("/tmp/docelem-index")).toPath )
  val config = new IndexWriterConfig(analyzer)
  val iwriter = new IndexWriter(directory, config)
  iwriter.commit
  val ireader = DirectoryReader.open(directory)
  val isearcher = new IndexSearcher(ireader)

  def fetchDocElemPayload(uuid: String): DocElemPayload = time(s"Lucene:fetch:$uuid") {
    val parser = new QueryParser("uuid", analyzer)
    val query = parser.parse(uuid)
    val hits = isearcher.search(query, null, 1).scoreDocs
    // TODO: search newest document
    // http://stackoverflow.com/questions/22564763/lucene-get-newest-document-for-category
    if (hits.length > 0) {
      val doc = isearcher.doc(hits(0).doc)
      DocElemPayload(uuid, doc.get("typ"), doc.get("model"))
    } else {
      DocElemPayload("", "", "")
    }
  }

  def saveDocElemPayloads(deps: Seq[DocElemPayload]): Seq[DocElemPayload] = time("Lucene:save") {
    deps.foreach { dep =>
      val hash = hasher(List("dump-import"), dep.model) // BLOOM?
      // We must check somewhere if there is for this model another uuid (only for automated imports!)
      val doc = new Document()
      doc.add(new Field("hash", hash, Field.Store.YES, Field.Index.NOT_ANALYZED))
      doc.add(new Field("uuid", dep.uuid, Field.Store.YES, Field.Index.NOT_ANALYZED))
      doc.add(new Field("typ", dep.typ, Field.Store.YES, Field.Index.NOT_ANALYZED))
      doc.add(new Field("model", dep.model, TextField.TYPE_STORED))
      iwriter.updateDocument(new Term("hash", hash), doc)  // no duplicates
    }
    iwriter.commit
    deps
  }

  import Annotation._
  def annotate(ids: ConnectedVs, sem: Semantics, otherProps: Map[String, Any]): Unit = time("Lucene:annotate") {
    val parser = new QueryParser("uuid", analyzer)
    val fromQuery = parser.parse(ids.fromUUID)
    val toQuery = parser.parse(ids.toUUID)
    val from = isearcher.search(fromQuery, null, 1).scoreDocs
    val to = isearcher.search(toQuery, null, 1).scoreDocs

    if (from.length > 0 && to.length > 0) {
      val fromDoc = isearcher.doc(from(0).doc)
      val toDoc = isearcher.doc(to(0).doc)

      // create Annotaiton
      val hash = hasher(
        List(ids.fromUUID, ids.toUUID, sem.purpose, sem.layer),
        fromDoc.get("hash")
      ) // BLOOM?
      // We must check somewhere if there is for this model another uuid (only for automated imports!)
      val doc = new Document()
      doc.add(new Field("hash", hash, Field.Store.YES, Field.Index.NOT_ANALYZED))
      doc.add(new Field("layer", sem.layer, Field.Store.YES, Field.Index.NOT_ANALYZED))
      doc.add(new Field("purpose", sem.purpose, Field.Store.YES, Field.Index.NOT_ANALYZED))
      // in future add other properties, like position and other individual props
      iwriter.updateDocument(new Term("hash", hash), doc)  // no duplicates
    }

    iwriter.commit
  }
}
