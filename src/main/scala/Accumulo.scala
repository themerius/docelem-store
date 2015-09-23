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

import scala.util.hashing.MurmurHash3
import scala.math.BigInt
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

  // CREATE TABLEs
  val ops = conn.tableOperations()
  if (!ops.exists("timemachine")) {
    ops.create("timemachine")
  }
  if (!ops.exists("dedupes")) {
    ops.create("dedupes")
  }
  if (!ops.exists("annotations")) {
    ops.create("annotations")
  }

  // GRANT permissions
  val secOps = conn.securityOperations()
  val newRootAuths = new Authorizations("public")
  secOps.changeUserAuthorizations("root", newRootAuths)

  val config = new BatchWriterConfig
  config.setMaxMemory(10000000L); // bytes available to batchwriter for buffering mutations
  val writerTimeMachine = conn.createBatchWriter("timemachine", config)
  val writerDedupes = conn.createBatchWriter("dedupes", config)
  val writerAnnotations = conn.createBatchWriter("annotations", config)

  def fetchDocElemPayload(uuid: String): DocElemPayload = time(s"Accumulo:fetch:$uuid") {
    val auths = new Authorizations()
    val scan = conn.createScanner("timemachine", auths)
    scan.setRange(new Range("scai.fhg.de"))
    scan.fetchColumn(new Text("Abstract"), new Text(uuid))

    val found = for (entry <- scan) yield {
        val key = entry.getKey()
        val value = entry.getValue()

        val scanDedupes = conn.createScanner("dedupes", auths)
        scanDedupes.setRange(new Range(value.toString))  // ^= hash
        scanDedupes.fetchColumn(new Text("scai.fhg.de"), new Text("Abstract/" + uuid))

        val content = for (entryDedupes <- scanDedupes) yield {
          entryDedupes.getValue().toString
        }

        if (content.size > 0)
          (uuid, "Abstract", content.toList(0))
        else
          (uuid, "Abstract", "")
    }

    if (found.size > 0)
      DocElemPayload.tupled(found.toList(0))
    else
      DocElemPayload("", "", "")
  }

  def saveDocElemPayloads(deps: Seq[DocElemPayload]): Seq[DocElemPayload] = time("Accumulo:save") {
    deps.foreach { dep =>
      val hash = MurmurHash3.stringHash(dep.model).toString.getBytes  // Use BigInt toByteArray?
      val authority = "scai.fhg.de".getBytes
      val typeUid = s"${dep.typ}/${dep.uuid}".getBytes

      // Optional:
      // val colVis = new ColumnVisibility("public")
      // val timestamp = System.currentTimeMillis()

      val mutation = new Mutation(hash)  // hash equals row id
      mutation.put(authority, typeUid, dep.model.getBytes)
      writerDedupes.addMutation(mutation)

      val mutationTM = new Mutation(authority)
      mutationTM.put(dep.typ.getBytes, dep.uuid.getBytes, hash)
      writerTimeMachine.addMutation(mutationTM)
    }

    writerDedupes.flush
    writerTimeMachine.flush
    deps
  }

  import Annotation._
  def annotate(ids: ConnectedVs, sem: Semantics, otherProps: Map[String, Any]): Unit = time("Accumulo:annotate") {
    ;
  }
}
