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

object AccumuloConnectionFactory {

  val conf = ConfigFactory.load

  val instanceName = conf.getString("docelem-store.storage.accumulo.instanceName")
  val zooServers = conf.getString("docelem-store.storage.accumulo.zooServers")
  val user = conf.getString("docelem-store.storage.accumulo.user")
  val pwd = conf.getString("docelem-store.storage.accumulo.pwd")

  // START embedded instance or connect to remote
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

  // docelem_uri
  //   :: layer_uri
  //   :: attribute_uri \0 model_spec_uri \0 fingerprint
  //   :: domain_model
  if (!ops.exists("knowledge_artifacts")) {
    ops.create("knowledge_artifacts")
    ops.setProperty("knowledge_artifacts", "table.bloom.enabled", "true")
  }

  // shard_id
  //   :: layer_uri ! attribute_uri
  //   :: docelem_uri
  //   :: nil
  if (!ops.exists("semantic_index")) {
    ops.create("semantic_index")
    ops.setProperty("semantic_index", "table.bloom.enabled", "true")
  }

  // GRANT permissions
  val secOps = conn.securityOperations()
  val newRootAuths = new Authorizations("public")
  secOps.changeUserAuthorizations("root", newRootAuths)

  def get = conn

}
