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

import java.io.File
import java.lang.Iterable

import eu.themerius.docelemstore.utils.Stats.time

case class WriteDocelems(dedupes: Iterable[Mutation], versions: Iterable[Mutation], size: Int)

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
  if (!ops.exists("annotations")) {  // TODO explicit allow only one version
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
  }

}
