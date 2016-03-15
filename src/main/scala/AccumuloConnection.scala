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
import org.apache.accumulo.core.client.Durability

import org.apache.hadoop.io.Text

import java.io.File
import java.lang.Iterable
import java.util.Collections

import scala.collection.JavaConverters._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import com.typesafe.config.ConfigFactory

import eu.themerius.docelemstore.utils.Stats.time

object AccumuloConnection {

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

  def get = conn

}
