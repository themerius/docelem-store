package eu.themerius.docelemstore

import akka.actor.{ ActorRef, ActorSystem, Props, Actor, Inbox }

import org.apache.accumulo.core.data.Mutation
import org.apache.accumulo.minicluster.MiniAccumuloCluster
import org.apache.accumulo.core.client.ZooKeeperInstance
import org.apache.accumulo.core.client.security.tokens.PasswordToken
import org.apache.accumulo.core.security.ColumnVisibility
import org.apache.accumulo.core.data.Value
import org.apache.accumulo.core.data.Mutation
import org.apache.accumulo.core.client.BatchWriterConfig
import org.apache.accumulo.core.security.Authorizations
import org.apache.accumulo.core.data.Range
import org.apache.accumulo.core.data.Key
import org.apache.accumulo.core.client.IteratorSetting
import org.apache.accumulo.core.iterators.user.IntersectingIterator

import org.apache.hadoop.io.Text

import java.io.File
import java.lang.Iterable
import java.util.Collections

import scala.collection.JavaConverters._

import com.typesafe.config.ConfigFactory

import eu.themerius.docelemstore.utils.Stats.time

// For writing into database
case class WriteDocelems(dedupes: Iterable[Mutation], versions: Iterable[Mutation], size: Int)
case class WriteAnnotations(annots: Iterable[Mutation], index: Iterable[Mutation], size: Int)

// For querying the database
case class FindDocelem(authority: String, typ: String, uid: String, replyTo: String, trackingNr: String, gate: ActorRef)
case class DiscoverDocelemsWithAnnotations(searchedLayers: Seq[String], annotUiids: Seq[String], replyTo: String, trackingNr: String, gate: ActorRef)
class AccumuloStorage extends Actor {

  println(s"Storage ${context.dispatcher}")

  val conf = ConfigFactory.load

  val instanceName = conf.getString("docelem-store.storage.accumulo.instanceName")
  val zooServers = conf.getString("docelem-store.storage.accumulo.zooServers")
  val user = conf.getString("docelem-store.storage.accumulo.user")
  val pwd = conf.getString("docelem-store.storage.accumulo.pwd")

  // START embedded instance
  val inst = if (conf.getBoolean("docelem-store.storage.accumulo.embedded")) {
    val dict = new File("/tmp/accumulo-mini-cluster")
    val accumulo = new MiniAccumuloCluster(dict, instanceName)
    accumulo.start
    println("Started embedded Accumulo instance.")
    new ZooKeeperInstance(accumulo.getInstanceName, accumulo.getZooKeepers)
  } else {
    new ZooKeeperInstance(instanceName, zooServers)
  }

  // CONNECT to instance
  val conn = if (conf.getBoolean("docelem-store.storage.accumulo.embedded")) {
    inst.getConnector(user, new PasswordToken(instanceName))
  } else {
    inst.getConnector(user, new PasswordToken(pwd))
  }

  // CREATE tables
  val ops = conn.tableOperations()
  if (!ops.exists("timemachine_v3")) {
    ops.create("timemachine_v3")
    // -schema-> authority : type : uid, hash
    // allow infinite versions (Accumulo Book, p.117)
    ops.removeProperty("timemachine_v3", "table.iterator.majc.vers.opt.maxVersions")
    ops.removeProperty("timemachine_v3", "table.iterator.minc.vers.opt.maxVersions")
    ops.removeProperty("timemachine_v3", "table.iterator.scan.vers.opt.maxVersions")
    // ops.setProperty("annotations_v3", "table.iterator.majc.vers.opt.maxVersions", "1000")
    // ops.setProperty("annotations_v3", "table.iterator.minc.vers.opt.maxVersions", "1000")
    // ops.setProperty("annotations_v3", "table.iterator.scan.vers.opt.maxVersions", "1000")
  }
  if (!ops.exists("dedupes_v3")) {
    ops.create("dedupes_v3")
    // -schema-> hash : authority : type/uid, model payload as xml
  }
  if (!ops.exists("annotations_v3")) {
    ops.create("annotations_v3")
    // -schema-> from/fromVersion : layer : purposeHash, annotation payload as xml
  }
  if (!ops.exists("annotations_index_v3")) {
    ops.create("annotations_index_v3")
    // -schema-> layer as shardID : to : from/fromVersion, annotation payload as xml
    // -schema-> to : layer : from/fromVersion, annotation payload as xml
    // -schema-> payloadHash : to : from, annotation payload as xml  # payloadHash contains only layer/purpose/..? so that more (similar) annotations are grouped
  }

  // GRANT permissions
  val secOps = conn.securityOperations()
  val newRootAuths = new Authorizations("public")
  secOps.changeUserAuthorizations("root", newRootAuths)

  // SETUP writers
  val config = new BatchWriterConfig
  config.setMaxMemory(52428800L); // bytes available to batchwriter for buffering mutations
  val writerTimeMachine = conn.createBatchWriter("timemachine_v3", config)
  val writerDedupes = conn.createBatchWriter("dedupes_v3", config)
  val writerAnnotations = conn.createBatchWriter("annotations_v3", config)
  val writerAnnotationsIndex = conn.createBatchWriter("annotations_index_v3", config)

  var d = 0
  var a = 0

  def receive = {

    case WriteDocelems(dedupes, versions, size) => time (s"Accumulo:WriteDocelems($d)") {
      writerDedupes.addMutations(dedupes)
      writerTimeMachine.addMutations(versions)
      d = d + 1
    }

    case WriteAnnotations(annots, index, size) => time (s"Accumulo:WriteAnnotations($a)") {
      writerAnnotations.addMutations(annots)
      writerAnnotationsIndex.addMutations(index)
      a = a + 1
    }

    case "FLUSH" => time ("Accumulo:FLUSH") {
      writerDedupes.flush
      writerTimeMachine.flush
      writerAnnotations.flush
      writerAnnotationsIndex.flush
    }

    case FindDocelem(authority, typ, uid, replyTo, trackingNr, gate) => time (s"Accumulo:FindDocelem") {
      val found = scanSingleDocelem(authority, typ, uid)
      found.map {
        xml => {  // TODO is too ulgy!
          val annots = scanAnnotations(xml \\ "uiid" text, xml \ "@version" text)
          val hack = "<corpus>" + xml.toString + annots.mkString + "</corpus>"
          gate ! Reply(hack, replyTo, trackingNr)
        }
      }
    }

    case DiscoverDocelemsWithAnnotations(layers, annotations, replyTo, trackingNr, gate) => time (s"Accumulo:DiscoverDocelemsWithAnnotations") {
      val found = scanAnnotationsIndex(layers, annotations)
      val xml =
      <results>{
        found.map { uiid =>
          <uiid>{uiid}</uiid>
        }
      }</results>
      gate ! Reply(xml.toString, replyTo, trackingNr)
    }

  }

  def scanSingleDocelem(authority: String, typ: String, uid: String) = {
    val auths = new Authorizations()
    val scan = conn.createScanner("timemachine_v3", auths)
    scan.setRange(Range.exact(authority))
    scan.fetchColumn(new Text(typ), new Text(uid))

    for (entry <- scan.asScala) yield {
        val key = entry.getKey()
        val value = entry.getValue()

        val scanDedupes = conn.createScanner("dedupes_v3", auths)
        scanDedupes.setRange(Range.exact(value.toString))  // ^= hash
        scanDedupes.fetchColumn(new Text(authority), new Text(typ + "/" + uid))

        val content = for (entryDedupes <- scanDedupes.asScala) yield {
          entryDedupes.getValue().toString
        }

        if (content.size > 0)
          <docelem version={value.toString}>
            <uiid>{authority + "/" + typ + "/" + uid}</uiid>
            <model>{scala.xml.Unparsed(content.mkString(""))}</model>
          </docelem>
        else
          <docelem></docelem>
    }
  }

  def scanAnnotations(uiid: String, version: String) = {
    val auths = new Authorizations()
    val scan = conn.createScanner("annotations_v3", auths)
    scan.setRange(new Range(uiid, uiid + "/" + version))
    for (entry <- scan.asScala) yield {
      entry.getValue
    }
  }

  /*
   * This will only intersect the terms within the same row!
  */
  def scanAnnotationsIndex(layers: Seq[String], uiids: Seq[String]) = {
    val tableName = "annotations_index_v3"
    val authorizations = new Authorizations()
    val numQueryThreads = 3
    val bs = conn.createBatchScanner(tableName, authorizations, numQueryThreads)

    val terms = uiids.map(uiid => new Text(uiid)).toArray

    val priority = 20
    val name = "ii"
    val iteratorClass = classOf[IntersectingIterator]
    val ii = new IteratorSetting(priority, name, iteratorClass)
    IntersectingIterator.setColumnFamilies(ii, terms)  // side effect on ii!

    bs.addScanIterator(ii)

    if (layers.isEmpty) {
      bs.setRanges(Collections.singleton(new Range()))  // all ranges
    } else {
      bs.setRanges(layers.map(Range.exact(_)).asJava)
    }

    for (entry <- bs.asScala.take(100)) yield {
      entry.getKey.getColumnQualifier.toString
    }
  }

  def scanTopologyOfDocelems(authority: String, typ: String, uid: String) = {
    Nil
  }

}
