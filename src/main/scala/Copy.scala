package eu.themerius.docelemstore

import eu.themerius.docelemstore.AccumuloConnectionFactory
import org.apache.accumulo.core.client.BatchWriterConfig
import scala.collection.JavaConverters._
import org.apache.accumulo.core.security.Authorizations
import org.apache.accumulo.core.data.Mutation


object Copy {
  val FROM = "knowledge_artifacts_7_2_SNAPSHOT"
  val TO = "knowledge_artifacts_7_4_SNAPSHOT"

  val auths = new Authorizations()
  val scan = AccumuloConnectionFactory.get.createScanner(FROM, auths)

  val configWriter = new BatchWriterConfig
  configWriter.setMaxMemory(1024L * 1024L * 1024L)
  configWriter.setMaxWriteThreads(10)
  val artifactsWriterTO = AccumuloConnectionFactory.get.createBatchWriter(TO, configWriter)
  val artifactsWriterFROM = AccumuloConnectionFactory.get.createBatchWriter(FROM, configWriter)

  var nofMutations = 0
  for (entry <- scan.asScala) {

    val mutation = new Mutation(entry.getKey.getRow)
    mutation.put(entry.getKey.getColumnFamily, entry.getKey.getColumnQualifier, entry.getValue)

    artifactsWriterTO.addMutation(mutation)

    val mutationDel = new Mutation(entry.getKey.getRow)
    mutationDel.putDelete(entry.getKey.getColumnFamily, entry.getKey.getColumnQualifier)

    artifactsWriterFROM.addMutation(mutationDel)

    nofMutations = nofMutations + 1
    if (nofMutations % 1000 == 0) {
      println(nofMutations)
    }
  }
  println(nofMutations)
  println("Finished")

}


object Copy2 {
  val FROM = "topology_index_7_2_SNAPSHOT"
  val TO = "topology_index_7_4_SNAPSHOT"

  val auths = new Authorizations()
  val scan = AccumuloConnectionFactory.get.createScanner(FROM, auths)

  val configWriter = new BatchWriterConfig
  val artifactsWriter = AccumuloConnectionFactory.get.createBatchWriter(TO, configWriter)

  var nofMutations = 0
  for (entry <- scan.asScala) {

    val mutation = new Mutation(entry.getKey.getRow)
    mutation.put(entry.getKey.getColumnFamily, entry.getKey.getColumnQualifier, entry.getValue)

    artifactsWriter.addMutation(mutation)

    nofMutations = nofMutations + 1
    if (nofMutations % 1000 == 0) {
      println(nofMutations)
    }
  }
  println(nofMutations)
  println("Finished")

}
