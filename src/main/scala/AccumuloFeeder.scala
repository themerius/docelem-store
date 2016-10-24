package eu.themerius.docelemstore

import akka.actor.{ Actor }
import akka.event.Logging
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import org.apache.accumulo.core.data.Mutation
import org.apache.accumulo.core.client.BatchWriterConfig

import org.apache.hadoop.io.Text

import scala.util.hashing.MurmurHash3
import scala.collection.JavaConverters._

import java.net.URI

object TableType extends Enumeration {
  type TableType = Value
  val Artifacts, Semantic, Topology = Value
}
import TableType._

case class Add2Accumulo(corpus: Corpus, table: TableType, flush: Boolean = false)
case class AddRawData2Accumulo(model: ModelTransRules)


// Compaion Object for AccumuloFeeder. The accumulo writere should be shared by all AccumuloFeeder Actors (because they are thread safe and to safe resources...)
object AccumuloFeeder {

  val NUM_PARTITIONS = 32

  val configWriter = new BatchWriterConfig
  configWriter.setMaxMemory(1024L * 1024L * 1024L) // means 1024 MB
  configWriter.setMaxWriteThreads(10)

  val artifactsWriter = AccumuloConnectionFactory.get.createBatchWriter(AccumuloConnectionFactory.ARTIFACTS, configWriter)
  val indexWriter = AccumuloConnectionFactory.get.createBatchWriter(AccumuloConnectionFactory.SEMANTIC_INDEX, configWriter)
  val topologyIndexWriter = AccumuloConnectionFactory.get.createBatchWriter(AccumuloConnectionFactory.TOPOLOGY_INDEX, configWriter)

}


class AccumuloFeeder extends Actor {

  import AccumuloFeeder._

  val NUM_PARTITIONS = 32

  val log = Logging(context.system, this)

  def receive = {

    // TODO: extract Transform2DocElem into an dedicated Actor??
    case Transform2DocElem(model, data) => {
      // Transform a (domain specific) model into a DocElem-Corpus.
      model.deserialize(data)
      val corpus = model.applyRules

      // Transform DocElem-Corpus into Accumulo Datastructures.
      feedAccumulo( Add2Accumulo(corpus, Artifacts) )
      feedAccumulo( Add2Accumulo(corpus, Semantic) )
      feedAccumulo( Add2Accumulo(corpus, Topology) )

      // Add also the raw data (like XMI) to Accumulo
      feedAccumulo( AddRawData2Accumulo(model) )
    }

  }

  def feedAccumulo(obj: AddRawData2Accumulo) = obj match {

    case AddRawData2Accumulo(model) => {

      if (model.getDocumentId.nonEmpty) {

        val mutation = new Mutation(model.getDocumentId.get)

        if (model.rawTextMiningData.nonEmpty) {
          val rawData = model.rawTextMiningData.get
          val columnQualifier = s"textmining\0${rawData.dtype}\0${0}"
          mutation.put("raw_data".getBytes, columnQualifier.getBytes, rawData.data)
        }

        if (model.rawPlaintextData.nonEmpty) {
          val rawData = model.rawPlaintextData.get
          val columnQualifier = s"plaintext\0${rawData.dtype}\0${0}"
          mutation.put("raw_data".getBytes, columnQualifier.getBytes, rawData.data)
        }

        if (model.rawOriginalData.nonEmpty) {
          val rawData = model.rawOriginalData.get
          val columnQualifier = s"original\0${rawData.dtype}\0${0}"
          mutation.put("raw_data".getBytes, columnQualifier.getBytes, rawData.data)
        }

        artifactsWriter.addMutation(mutation)
        log.info(s"(artifactsWriter) has written a mutation with raw data.")

      }

    }

  }

  def feedAccumulo(obj: Add2Accumulo) = obj match {

    case Add2Accumulo(corpus: Corpus, Artifacts, flush) => {
      val mutations = corpus.artifacts.map { artifact =>

        val sigmatics = artifact.sigmatics.toString.getBytes
        val pragmatics = artifact.pragmatics.toString.getBytes

        val semantics = artifact.semantics
        val spec = artifact.meta.specification
        val fingerprint = Integer.toHexString(artifact.meta.fingerprint)

        val columnQualifier = s"${semantics}\0${spec}\0${fingerprint}".getBytes

        val mutation = new Mutation(sigmatics)
        mutation.put(pragmatics, columnQualifier, artifact.model)
        mutation

      }

      // send mutations to accumulo
      artifactsWriter.addMutations(mutations.toIterable.asJava)
      if (flush) artifactsWriter.flush
      log.info(s"(artifactsWriter) has written ${mutations.size} mutations.")
    }

    case Add2Accumulo(corpus: Corpus, Semantic, flush) => {
      val indexFilter = Set( new URI("annotation@v1") )
      val filteredCorpus = corpus.artifacts.view.filter {
        artifact => indexFilter.contains(artifact.meta.specification)
      }

      // Map of: docElemId -> (docElemId, documentHeaderId)
      val docElemId_to_documentHeaderIds = corpus.artifacts
        .filter(_.meta.specification.toString.startsWith("topo"))
        .map(item => (item.sigmatics.toString, item.pragmatics.toString.split("@tag:").head))
        .groupBy(_._1)

      val mutations = filteredCorpus.map { artifact =>
        val layerAndAttribute = s"${artifact.pragmatics}!${artifact.semantics}"
        val docElemId = artifact.sigmatics.toString
        val documentHeaderIds = docElemId_to_documentHeaderIds(docElemId).map(_._2).toSet

        // Schema:
        // PARTITON : layer!attribute : docElemId : (None)

        for (documentHeaderId <- documentHeaderIds) yield {
          // calculate a partition ID:
          // every entry with the same 'document id' (means doc elem id)
          // will be placed into the same partition
          val partitionId = Math.abs(MurmurHash3.stringHash(documentHeaderId)) % NUM_PARTITIONS
          val mutation = new Mutation(partitionId.toString)
          // here we merge the annotations into the "entire document"
          mutation.put(layerAndAttribute, documentHeaderId, "")
          // TODO: in future we want query annotations on sections or sentences (= the leafs)... Maybe we want that in an extra table! Implemenation for sentences/leafs will look like this:
          // mutation.put(layerAndAttribute, docElemId, "")
          mutation
        }

      }

      // send mutations to accumulo
      indexWriter.addMutations(mutations.flatten.toIterable.asJava)
      if (flush) indexWriter.flush
      log.info(s"(indexWriter) has written ${mutations.size} mutations.")
    }

    case Add2Accumulo(corpus: Corpus, Topology, flush) => {
      val topologyArtifacts = corpus.artifacts.filter(_.meta.specification.toString.startsWith("topo")).groupBy(_.pragmatics)

      val mutations = topologyArtifacts.map{ case(topologyTagId, artifacts) =>
        val mutation = new Mutation(topologyTagId.toString)
        artifacts.foreach{ artifact =>
          mutation.put("contains", artifact.sigmatics.toString, "")
        }
        mutation
      }
      // send mutations to accumulo
      topologyIndexWriter.addMutations(mutations.toIterable.asJava)
      if (flush) topologyIndexWriter.flush
      log.info(s"(topologyIndexWriter) has written ${mutations.size} mutations.")
    }

  }

}
