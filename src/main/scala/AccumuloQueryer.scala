package eu.themerius.docelemstore

import akka.actor.{ Actor }
import akka.event.Logging

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import org.apache.accumulo.core.security.Authorizations
import org.apache.accumulo.core.data.Range
import org.apache.hadoop.io.Text
import org.apache.accumulo.core.client.IteratorSetting
import org.apache.accumulo.core.iterators.user.IntersectingIterator

import scala.collection.JavaConverters._
import scala.xml.PrettyPrinter
import scala.util.hashing.MurmurHash3

import java.net.URI
import java.lang.Long
import java.util.ArrayList
import java.util.HashSet
import java.util.Collections

import utils.Stats.time
import utils.PMIDLexicoder

import QueryTarget.SingleDocElem
import QueryTarget.Topology
import QueryTarget.TopologyOnlyHierarchy
import QueryTarget.SemanticSearch

case class BuildQuery(builder: QueryBuilder, data: Array[Byte], reply: Reply)
case class Scan(query: Query, reply: Reply)
case class PrepareReply(corpus: Corpus, reply: Reply)
case class PrepareReplyOnlyHierarchy(xmlTopology: scala.xml.Node, reply: Reply)
case class SearchTerm(pragmatics: URI, semantics: URI)

// TODO: rename to AccumuloRetrieval?
class AccumuloQueryer extends Actor {

  val log = Logging(context.system, this)
  val lc = new PMIDLexicoder
  val xmlGenerator = new PrettyPrinter(80, 2)
  val version = s"${BuildInfo.name}:${BuildInfo.version}"

  def receive = {

    case BuildQuery(builder, data, reply) => {
      builder.deserialize(data)
      val query = builder.buildQuery
      log.debug(query.toString)
      self ! Scan(query, reply)
    }

    case Scan(Query(SingleDocElem, queryXml), reply) => {
      val text = (queryXml \\ "meta" \ "@content")(0).text
      val uri = new URI(text)
      log.info(s"(Query/SingleDocElem) scanning for ${uri}.")

      val corpus = scanSingleDocelem(uri)
      if (corpus.artifacts.size > 0) {
        self ! PrepareReply(corpus, reply)
        log.info(s"(Query/SingleDocElem) found ${corpus.artifacts.size} artifacts.")
      }
    }

    case Scan(Query(Topology, queryXml), reply) => {
      val text = (queryXml \\ "meta" \ "@content")(0).text
      val uri = new URI(text)
      log.info(s"(Query/Topology) scanning for ${uri}.")

      val corpus = scanTopology(uri)
      if (corpus.artifacts.size > 0) {
        self ! PrepareReply(corpus, reply)
        log.info(s"(Query/Topology) found ${corpus.artifacts.size} artifacts.")
      }
    }

    case Scan(Query(TopologyOnlyHierarchy, queryXml), reply) => {
      val text = (queryXml \\ "meta" \ "@content")(0).text
      val uri = new URI(text)
      log.info(s"(Query/TopologyOnlyHierarchy) scanning for ${uri}.")

      val xmlTopo = scanTopologyOnlyHierarchy(uri)
      if ((xmlTopo \ "docelem").size > 0) {
        self ! PrepareReplyOnlyHierarchy(xmlTopo, reply)
        log.info(s"(Query/TopologyOnlyHierarcy) the hierarcy contains ${(xmlTopo \ "docelem").size} elements.")
      }
    }

    case Scan(Query(SemanticSearch, queryXml), reply) => {
      val terms = (queryXml \\ "uri")
        .map(uri => (uri \ "@layer").text -> uri.text)
        .map(tuple => SearchTerm(new URI(tuple._1), new URI(tuple._2)))
        .toSet
      log.info(s"(Query/SemanticSearch) scanning for ${terms}.")

      val queryId = (queryXml \\ "@uri").text
      val findings = scanSemanticIndex(terms)
      val lis = findings.map{ finding =>
        val href = s"/§§:$finding"
        <li><a href={href}>{finding}</a></li>
      }
      val answerHtml = <ol>{lis}</ol>

      val corpus = Corpus(generateQueryDocElems(queryId, findings, answerHtml.toString, xmlGenerator.format(queryXml)))
      println(corpus)

      context.parent ! Add2Accumulo(corpus, TableType.Artifacts, true)
      context.parent ! Add2Accumulo(corpus, TableType.Topology, true)

      //self ! PrepareReplyOnlyHierarchy(xmlTopo, reply)
      log.info(s"(Query/SemanticSearch) found ${findings.size} elements and findings are in docelem ${queryId}.")
    }

    case PrepareReply(corpus, reply) => {
      // TODO: refactor Reply(answer, channel, trackingNr). create extra channel class which can test if the cannel string is valid?

      // Map[URI,Seq]: docelem -> Seq(KnowledgeArtifact)
      val docelems = corpus.artifacts.groupBy(_.sigmatics)
      // Map[URI, Map[URI, Seq]]: docelem -> layer -> Seq(KnowledgeArtifact)
      val layers = docelems.map{case (k,v) => k -> v.groupBy(_.pragmatics)}

      // TODO: this needs some clean code here... and put into extra method/class, which is also better for testing...
      // <corpus>
      //   <docelem>
      //      <layer>
      //        <attr><attr>...
      val xmlCorpus =
        <corpus by={version}>{layers.map(de => <docelem uri={de._1.toString}>{de._2.map(la => <layer uri={la._1.toString}>{la._2.map(ar => <attr uri={ar.semantics.toString} spec={ar.meta.specification.toString} layer={la._1.toString}>{scala.xml.PCData(new String(ar.model, "UTF-8"))}</attr>)}</layer>)}</docelem>)}</corpus>

      context.parent ! Reply(xmlGenerator.format(xmlCorpus), reply.to, reply.trackingNr)
      log.info(s"(Reply) send to ${reply.to}.")
    }

    // TODO: awww, nice hack!
    case PrepareReplyOnlyHierarchy(xmlTopology, reply) => {
      context.parent ! Reply(xmlGenerator.format(xmlTopology), reply.to, reply.trackingNr)
      log.info(s"(ReplyOnlyHierarchy) send to ${reply.to}.")
    }
  }

  def scanSingleDocelem(uri: URI): Corpus = {
    val auths = new Authorizations()
    val scan = AccumuloConnectionFactory.get.createScanner(AccumuloConnectionFactory.ARTIFACTS, auths)
    val uriLexicoded = new String(lc.encode(uri.toString))
    scan.setRange(Range.exact(uriLexicoded))
    //scan.fetchColumn(new Text(typ), new Text(uid))

    val artifacts =
      for (entry <- scan.asScala) yield {
        val key = entry.getKey()
        val value = entry.getValue()

        val qual = key.getColumnQualifier.toString
        val (semantics, spec, fingerprint) = qual.split("\u0000") match {
          case Array(semantics, spec, fingerprint) => (semantics, spec, fingerprint)
          case _ => ("", "", "0")
        }

        KnowledgeArtifact(
          new URI(lc.decode(key.getRow.getBytes)),
          new URI(key.getColumnFamily.toString),
          new URI(semantics),
          value.get,
          Meta(new URI(spec), Long.parseLong(fingerprint, 16).toInt)
        )
      }

    Corpus(artifacts.toSeq)
  }

  def getLatestTopologyTag(uri: URI) = time("getLatestTopologyTag") {
    val auths = new Authorizations()
    val scanHead = AccumuloConnectionFactory.get.createScanner(AccumuloConnectionFactory.ARTIFACTS, auths)
    val uriLexicoded = new String(lc.encode(uri.toString))
    scanHead.setRange(Range.exact(uriLexicoded))

    val result = scanHead.asScala
      .filter(_.getKey.getColumnFamily.toString.startsWith(uri.toString))
      .toSeq
      .sortBy(_.getKey.getTimestamp)
      .reverse

    val newestTopologyTag = if (result.nonEmpty) {
      result.head.getKey.getColumnFamily.toString
    } else {
      ""  // => this is a single document element
    }

    log.info(s"Newest topology tag is $newestTopologyTag")

    new URI(newestTopologyTag)
  }

  def scanTopology(uri: URI): Corpus = time("scanTopology") {

    val auths = new Authorizations()
    val uriLexicoded = new String(lc.encode(uri.toString))
    val latestTopology = getLatestTopologyTag(uri)
    val involvedIds = scanTopologyIndex(latestTopology)

    val ranges = new ArrayList[Range]()
    for (entry <- involvedIds) yield {
      // TODO: do we need here also lexicode for PMIDs?
      ranges.add(Range.exact(entry))
    }
    // add also the header of the topology
    ranges.add(Range.exact(uriLexicoded))

    val bscan = AccumuloConnectionFactory.get.createBatchScanner(AccumuloConnectionFactory.ARTIFACTS, auths, 10)
    bscan.setRanges(ranges)
    // bscan.fetchColumnFamily(new Text("_"))
    // bscan.fetchColumnFamily(new Text("mesh_disease_ns_mapped"))

    val bartifacts =
      for (entry <- bscan.asScala) yield {
        val key = entry.getKey()
        val value = entry.getValue()

        val qual = key.getColumnQualifier.toString
        val (semantics, spec, fingerprint) = qual.split("\u0000") match {
          case Array(semantics, spec, fingerprint) => (semantics, spec, fingerprint)
          case _ => ("", "", "0")
        }

        KnowledgeArtifact(
          new URI(lc.decode(key.getRow.getBytes)),
          new URI(key.getColumnFamily.toString),
          new URI(semantics),
          value.get,
          Meta(new URI(spec), Long.parseLong(fingerprint, 16).toInt)
        )
      }

    val header = bartifacts.filter(_.pragmatics == latestTopology)
    val filtered = bartifacts.filterNot(_.pragmatics.getPath.startsWith("header/"))

    Corpus((header ++ filtered).toSeq)

  }

  // TODO: restrict the resulting Corpus to only topology relevant infos/model (follows, rank etc.)
  def scanTopologyOnlyHierarchy(uri: URI): scala.xml.Node = time("scanTopologyOnlyHierarchy") {

    val auths = new Authorizations()
    val uriLexicoded = new String(lc.encode(uri.toString))
    val latestTopology = getLatestTopologyTag(uri)
    val involvedIds = scanTopologyIndex(latestTopology)

    val ranges = new ArrayList[Range]()
    for (entry <- involvedIds) yield {
      // TODO: do we need here also lexicode for PMIDs?
      ranges.add(Range.exact(entry))
    }
    // add also the header of the topology
    ranges.add(Range.exact(uriLexicoded))

    val bscan = AccumuloConnectionFactory.get.createBatchScanner(AccumuloConnectionFactory.ARTIFACTS, auths, 10)
    bscan.setRanges(ranges)

    // IMPORTANT: Get only the annotation layer containing the topology infos!
    bscan.fetchColumnFamily(new Text(latestTopology.toString))

    val docelems = for (entry <- bscan.asScala) yield {
      val key = entry.getKey
      val value = new String(entry.getValue.get)

      val row = lc.decode(key.getRow.getBytes)
      val qual = key.getColumnQualifier.toString
      val (semantics, spec, fingerprint) = qual.split("\u0000") match {
        case Array(semantics, spec, fingerprint) => (semantics, spec, fingerprint)
        case _ => ("", "", "0")
      }

      <docelem uri={row} follows={semantics} rank={value} />
    }

    <topology superordinate={uri.toString}>
      {docelems}
    </topology>

  }

  def scanTopologyIndex(latestTopology: URI) = time("scanTopologyIndex") {
    val auths = new Authorizations()
    val scan = AccumuloConnectionFactory.get.createScanner(AccumuloConnectionFactory.TOPOLOGY_INDEX, auths)
    //scan.setRange()
    scan.setRange(Range.exact(latestTopology.toString))
    scan.fetchColumnFamily(new Text("contains"))
    val involvedIds = scan.asScala.map(_.getKey.getColumnQualifier.toString).toSet
    log.info(s"There are ${involvedIds.size} docelems in the topology.")
    involvedIds
  }

  // TESTS
  //println(scanSemanticIndex(Set(SearchTerm(new URI("meddra"), new URI("concept/meddra:cancer_pain")), SearchTerm(new URI("meddra"), new URI("concept/meddra:failure_kidney")))))
  //println(scanSemanticIndex(Set(SearchTerm(new URI("meddra"), new URI("concept/meddra:consumption_coagulopathy")), SearchTerm(new URI("meddra"), new URI("concept/meddra:facial_flushing")))))

  //println(scanSemanticIndex(Set(SearchTerm(new URI("NDD_clinical_trial"), new URI("concept/NDD_clinical_trial:Cohort%20study%20design")), SearchTerm(new URI("MeSH_Disease_ns_mapped"), new URI("concept/MeSH_Disease_ns_mapped:Alzheimer's%20disease")))))




  /*
   * This will only intersect the terms within the same row!
  */
  def scanSemanticIndex(terms: Set[SearchTerm]) = time("semantic index") {
    log.info(s"(scanSemanticIndex) Search Terms: $terms")

    val requestedTable = terms
      .filter(_.pragmatics.toString.startsWith("semantic_search"))
      .map(_.semantics.toString)
      .headOption.getOrElse("0").toInt

    val tableName = requestedTable match {
      case 100 => AccumuloConnectionFactory.SEMANTIC_INDEX
      case 200 => AccumuloConnectionFactory.SEMANTIC_ELEM_INDEX
      case _   => AccumuloConnectionFactory.SEMANTIC_INDEX
    }

    val authorizations = new Authorizations()
    val numQueryThreads = 10
    val bs = AccumuloConnectionFactory.get.createBatchScanner(tableName, authorizations, numQueryThreads)

    // Assemble search terms (the will be found in the column familiy)
    val termsFiltered = terms.filterNot(_.pragmatics.toString.startsWith("semantic_search"))
    val searchTerms = termsFiltered.map(t => new Text( s"${t.pragmatics}!${t.semantics}") ).toArray

    val priority = 20
    val name = "ii"
    val iteratorClass = classOf[IntersectingIterator]
    val ii = new IteratorSetting(priority, name, iteratorClass)
    IntersectingIterator.setColumnFamilies(ii, searchTerms)  // side effect on ii!

    bs.addScanIterator(ii)
    bs.setRanges(Collections.singleton(new Range()))  // scan all partitions

    // return the first 100 findings
    for (entry <- bs.asScala.take(1000)) yield {
      entry.getKey.getColumnQualifier.toString
    }
  }

  def calculateCombinedHash(strs: Iterable[String]) = {
    val combinedHash = strs.foldLeft(0) { (total, item) =>
      // use hash from previous item as seed
      MurmurHash3.stringHash(item, total)
    }
    Integer.toHexString(combinedHash)
  }

  def generateQueryDocElems(queryId: String, findings: Iterable[String], answerHtml: String, queryXml: String) = {

    var count = 0
    // make a topology tag based on the findings set
    val hash = calculateCombinedHash(findings)

    val topos = for (finding <- findings) yield {
      count = count + 100
      KnowledgeArtifact(
        new URI(finding),
        new URI(queryId + s"@tag:$hash"),
        new URI(s"topo/$queryId"),
        count.toString.getBytes,
        Meta(new URI("topo-rank@v1"))
      )
    }

    topos.toSeq ++ Seq(
      KnowledgeArtifact(
        new URI(queryId),
        new URI(queryId + s"@tag:$hash"),
        new URI(s"topo/$queryId"),
        "-1000".getBytes,
        Meta(new URI("topo-rank@v1"))
      ),
      KnowledgeArtifact(
        new URI(queryId),
        new URI("_"),
        new URI("query/answer"),
        answerHtml.getBytes,
        Meta(new URI("html"))
      ),
      KnowledgeArtifact(
        new URI(queryId),
        new URI("_"),
        new URI("query/query"),
        queryXml.getBytes,
        Meta(new URI("semantic-query-dsl@v1"))
      )
    )
  }

}
