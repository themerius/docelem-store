package eu.themerius.docelemstore

import eu.themerius.docelemstore.utils.Stats.time

import org.apache.accumulo.minicluster.MiniAccumuloCluster
import org.apache.accumulo.core.client.mock.MockInstance

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

import scala.collection.JavaConverters._
import scala.collection.JavaConversions._

object AccumuloTry extends App {

  // First create table and give permissions (for visibility)
  // $ ./bin/accumulo shell -u root
  // > createtable table
  // > setauths -s public -u root

  // EXTERNAL INSTANCE
  //val instanceName = "test";
  //val zooServers = "localhost"//"zooserver-one,zooserver-two"
  //val inst = new ZooKeeperInstance(instanceName, zooServers);

  // EMBEDDED INSTANCE
  val dict = new File("/tmp/accumulo-mini-cluster")
  val accumulo = new MiniAccumuloCluster(dict, "test")
  accumulo.start
  val inst = new ZooKeeperInstance(accumulo.getInstanceName, accumulo.getZooKeepers)

  val conn = inst.getConnector("root", new PasswordToken("test"))

  // CREATE TABLE
  val ops = conn.tableOperations()
  if (!ops.exists("table")) {
    ops.create("table")
  }

  // GRANT permissions
  val secOps = conn.securityOperations()
  val newRootAuths = new Authorizations("public")
  secOps.changeUserAuthorizations("root", newRootAuths)


  println("Generating row.")
  val rowID = new Text("1212-0fb")
  val colFam = new Text("model")
  val colQual = new Text("0fb")
  val colVis = new ColumnVisibility("public")
  val timestamp = System.currentTimeMillis()

  val value = new Value("Other".getBytes())

  val mutation = new Mutation(rowID)
  mutation.put(colFam, colQual, colVis, timestamp, value)

  println("Push row.")
  val config = new BatchWriterConfig
  config.setMaxMemory(10000000L); // bytes available to batchwriter for buffering mutations

  val writer = conn.createBatchWriter("table", config)

  var x = 0
  while (x < 10) {
    time("add") {
      writer.addMutation(mutation);
    }
    x += 1
  }

  time ("close") {
    writer.close();
  }

  println("Scan.")
  val auths = new Authorizations("public");

  val scan = conn.createScanner("table", auths);

  scan.setRange(new Range("1212","1212-zzzzzzzzzz"));
  scan.fetchColumnFamily(new Text("model"));

  for(entry <- scan) {
      val row = entry.getKey().getRow();
      val ts = entry.getKey().getTimestamp()
      val value = entry.getValue();
      println(row, ts, value)
  }

  accumulo.stop
}

object Accumulo extends Database {
  //val instanceName = "test";
  //val zooServers = "localhost"//"zooserver-one,zooserver-two"
  //val inst = new ZooKeeperInstance(instanceName, zooServers);

  // enbedded instance
  val dict = new File("/tmp/accumulo-mini-cluster")
  val accumulo = new MiniAccumuloCluster(dict, "test")
  accumulo.start
  val inst = new ZooKeeperInstance(accumulo.getInstanceName, accumulo.getZooKeepers)

  val conn = inst.getConnector("root", new PasswordToken("test"))

  // CREATE TABLE
  val ops = conn.tableOperations()
  if (!ops.exists("test")) {
    ops.create("test")
  }

  // GRANT permissions
  val secOps = conn.securityOperations()
  val newRootAuths = new Authorizations("public")
  secOps.changeUserAuthorizations("root", newRootAuths)

  val config = new BatchWriterConfig
  config.setMaxMemory(10000000L); // bytes available to batchwriter for buffering mutations
  val writer = conn.createBatchWriter("test", config)

  def fetchDocElemPayload(uuid: String): DocElemPayload = time(s"Accumulo:fetch:$uuid") {
    val auths = new Authorizations("public")
    val scan = conn.createScanner("test", auths)
    scan.setRange(new Range(uuid,uuid))
    scan.fetchColumnFamily(new Text("model"))
    scan.fetchColumnFamily(new Text("typ")) // to fetch multiple column fams, call function multiple times...
    //scan.fetchColumnQualifier(new Text("text"))
    // TODO
    val found = for (entry <- scan) yield {
        val key = entry.getKey().getColumnFamily()
        val value = entry.getValue()
        key -> value
    }
    println(found)
    DocElemPayload("", "", "")
  }

  def saveDocElemPayloads(deps: Seq[DocElemPayload]): Seq[DocElemPayload] = time("Accumulo:save") {
    deps.foreach { dep =>
      val hash = hasher(List("dump-import"), dep.model) // BLOOM?
      val rowID = new Text(s"${dep.typ}/${dep.uuid}")
      val modelFam = new Text("model")
      val modelColQual = new Text(hash)
      val colVis = new ColumnVisibility("public")
      val timestamp = System.currentTimeMillis()
      val value = new Value(dep.model.getBytes)
      val mutation = new Mutation(rowID)
      mutation.put(modelFam, modelColQual, colVis, timestamp, value)
      writer.addMutation(mutation)
    }
    deps
  }

  import Annotation._
  def annotate(ids: ConnectedVs, sem: Semantics, otherProps: Map[String, Any]): Unit = time("Accumulo:annotate") {
    ;
  }
}
