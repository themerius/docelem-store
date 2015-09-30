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

import eu.themerius.docelemstore.utils.Stats.time

// For writing into database
case class WriteDocelems(dedupes: Iterable[Mutation], versions: Iterable[Mutation], size: Int)
case class WriteAnnotations(annots: Iterable[Mutation], size: Int)

// For querying the database
// TODO is ugly interface!
case class OuterScanner(authority: Range, typ: Text, uid: Text)
case class InnerScanner(authority: Text, typUid: Text)
case class FindDocelem(auths: Authorizations, os: OuterScanner, is: InnerScanner, replyTo: String, gate: ActorRef)

class AccumuloStorage extends Actor {

  // START embedded instance
  val dict = new File("/tmp/accumulo-mini-cluster")
  val accumulo = new MiniAccumuloCluster(dict, "test")
  accumulo.start
  val inst = new ZooKeeperInstance(accumulo.getInstanceName, accumulo.getZooKeepers)
  println("Started embedded Accumulo instance.")

  // CONNECT to instance
  val conn = inst.getConnector("root", new PasswordToken("test"))

  // CREATE tables
  val ops = conn.tableOperations()
  if (!ops.exists("timemachine")) {  // TODO allow infinite versions
    ops.create("timemachine")
  }
  if (!ops.exists("dedupes")) {  // TODO explicit allow only one version
    ops.create("dedupes")
  }
  if (!ops.exists("annotations")) {  // TODO explicit allow only one version??? or infinite??
    ops.create("annotations")
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

    case FindDocelem(auths, os, is, replyTo, gate) => time (s"Accumulo:FindDocelem") {
      val found = scanSingleDocelem(auths, os, is)
      found.map {
        xml => {  // TODO is too ulgy!
          val annots = scanAnnotations(xml \\ "uiid" text, xml \ "@version" text)
          val hack = "<corpus>" + xml.toString + annots.mkString + "</corpus>"
          gate ! Reply(hack, replyTo)
        }
      }
    }
  }

  def scanSingleDocelem(auths: Authorizations, os: OuterScanner, is: InnerScanner) = {
    val scan = conn.createScanner("timemachine", auths)
    scan.setRange(os.authority)
    scan.fetchColumn(os.typ, os.uid)

    for (entry <- scan.asScala) yield {
        val key = entry.getKey()
        val value = entry.getValue()

        val scanDedupes = conn.createScanner("dedupes", auths)
        scanDedupes.setRange(new Range(value.toString))  // ^= hash
        scanDedupes.fetchColumn(is.authority, is.typUid)

        val content = for (entryDedupes <- scanDedupes.asScala) yield {
          entryDedupes.getValue().toString
        }

        if (content.size > 0)
          <docelem version={value.toString}>
            <uiid>{"scai.fhg.de/" + os.typ.toString + "/" + os.uid.toString}</uiid>
            <model>{content}</model>
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

}
