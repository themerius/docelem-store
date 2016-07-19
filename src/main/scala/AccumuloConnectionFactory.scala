package eu.themerius.docelemstore

import org.apache.accumulo.minicluster.MiniAccumuloCluster
import org.apache.accumulo.core.client.ZooKeeperInstance
import org.apache.accumulo.core.client.security.tokens.PasswordToken
import org.apache.accumulo.core.security.Authorizations

import java.io.File

import com.typesafe.config.ConfigFactory

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
  val ARTIFACTS = "knowledge_artifacts_dev_sh"
  if (!ops.exists(ARTIFACTS)) {
    ops.create(ARTIFACTS)
    ops.setProperty(ARTIFACTS, "table.bloom.enabled", "true")
  }

  // shard_id
  //   :: layer_uri ! attribute_uri
  //   :: docelem_uri
  //   :: nil
  val SEMANTIC_INDEX = "semantic_index_dev"
  if (!ops.exists(SEMANTIC_INDEX)) {
    ops.create(SEMANTIC_INDEX)
    ops.setProperty(SEMANTIC_INDEX, "table.bloom.enabled", "true")
  }

  // GRANT permissions
  val secOps = conn.securityOperations()
  val newRootAuths = new Authorizations("public")
  secOps.changeUserAuthorizations("root", newRootAuths)

  def get = conn

}
