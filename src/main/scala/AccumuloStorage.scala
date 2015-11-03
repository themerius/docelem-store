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

import org.apache.hadoop.io.Text

import java.io.File
import java.lang.Iterable

import scala.collection.JavaConverters._

import com.typesafe.config.ConfigFactory

import eu.themerius.docelemstore.utils.Stats.time

// For writing into database
case class WriteDocelems(dedupes: Iterable[Mutation], versions: Iterable[Mutation], size: Int)
case class WriteAnnotations(annots: Iterable[Mutation], size: Int)

// For querying the database
case class FindDocelem(authority: String, typ: String, uid: String, replyTo: String, trackingNr: String, gate: ActorRef)

class AccumuloStorage extends Actor {

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
  val conn = inst.getConnector(user, new PasswordToken(pwd))

  // CREATE tables
  val ops = conn.tableOperations()
  if (!ops.exists("timemachine")) {  // TODO allow infinite versions
    ops.create("timemachine")
  }
  if (!ops.exists("dedupes")) {  // TODO explicit allow only one version
    ops.create("dedupes")
  }
  if (!ops.exists("annotations")) {
    ops.create("annotations")
    // Keep all versions (Accumulo Book, p.117)
    // ops.removeProperty("annotations", "table.iterator.majc.vers.opt.maxVersions")
    // ops.removeProperty("annotations", "table.iterator.minc.vers.opt.maxVersions")
    // ops.removeProperty("annotations", "table.iterator.scan.vers.opt.maxVersions")
    // ops.setProperty("annotations", "table.iterator.majc.vers.opt.maxVersions", "1000")
    // ops.setProperty("annotations", "table.iterator.minc.vers.opt.maxVersions", "1000")
    // ops.setProperty("annotations", "table.iterator.scan.vers.opt.maxVersions", "1000")
  }

  // GRANT permissions
  val secOps = conn.securityOperations()
  val newRootAuths = new Authorizations("public")
  secOps.changeUserAuthorizations("root", newRootAuths)

  // SETUP writers
  val config = new BatchWriterConfig
  config.setMaxMemory(10000000L); // bytes available to batchwriter for buffering mutations
  val writerTimeMachine = conn.createBatchWriter("timemachine", config)
  val writerDedupes = conn.createBatchWriter("dedupes", config)
  val writerAnnotations = conn.createBatchWriter("annotations", config)

  def receive = {
    case WriteDocelems(dedupes, versions, size) => time (s"Accumulo:WriteDocelems($size)") {
      writerDedupes.addMutations(dedupes)
      writerTimeMachine.addMutations(versions)
    }

    case WriteAnnotations(annots, size) => time (s"Accumulo:WriteAnnotations($size)") {
      writerAnnotations.addMutations(annots)
    }

    case "FLUSH" => time ("Accumulo:FLUSH") {
      writerDedupes.flush
      writerTimeMachine.flush
      writerAnnotations.flush
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
  }

  def scanSingleDocelem(authority: String, typ: String, uid: String) = {
    val auths = new Authorizations()
    val scan = conn.createScanner("timemachine", auths)
    scan.setRange(Range.exact(authority))
    scan.fetchColumn(new Text(typ), new Text(uid))

    for (entry <- scan.asScala) yield {
        val key = entry.getKey()
        val value = entry.getValue()

        val scanDedupes = conn.createScanner("dedupes", auths)
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
    val scan = conn.createScanner("annotations", auths)
    scan.setRange(new Range(uiid, uiid + "/" + version))
    for (entry <- scan.asScala) yield {
      entry.getValue
    }
  }

  def scanTopologyOfDocelems(authority: String, typ: String, uid: String) = {
    Nil
  }

}
