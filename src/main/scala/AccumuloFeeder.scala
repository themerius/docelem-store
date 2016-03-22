package eu.themerius.docelemstore

import akka.actor.{ Actor }
import akka.event.Logging

import org.apache.accumulo.core.data.Mutation
import org.apache.accumulo.core.client.BatchWriterConfig

import scala.util.hashing.MurmurHash3
import scala.collection.JavaConverters._

import java.net.URI


case class DocElem2Accumulo(corpus: Corpus)
case class Add2AnnotationIndex(corpus: Corpus)

class AccumuloFeeder extends Actor {

  val log = Logging(context.system, this)

  val NUM_PARTITIONS = 32

  val configWriter = new BatchWriterConfig
  val artifactsWriter = AccumuloConnectionFactory.get.createBatchWriter("knowledge_artifacts", configWriter)
  val indexWriter = AccumuloConnectionFactory.get.createBatchWriter("semantic_index", configWriter)

  def receive = {

    // TODO: extract Transform2DocElem into an dedicated Actor??
    case Transform2DocElem(model, data) => {
      // Transform a (domain specific) model into a DocElem-Corpus.
      model.deserialize(data)
      val corpus = model.applyRules

      // Transform DocElem-Corpus into Accumulo Datastructures.
      self ! DocElem2Accumulo(corpus)
      self ! Add2AnnotationIndex(corpus)
    }

    case DocElem2Accumulo(corpus: Corpus) => {
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
      log.info(s"(artifactsWriter) has written ${mutations.size} mutations.")
    }

    case Add2AnnotationIndex(corpus: Corpus) => {
      val indexFilter = Set(new URI("annotation@v1"), new URI("topology@v1"))
      val filteredCorpus = corpus.artifacts.view.filter {
        artifact => indexFilter.contains(artifact.meta.specification)
      }

      val mutations = filteredCorpus.map { artifact =>
        val layerAndAttribute = s"${artifact.pragmatics}!${artifact.semantics}"
        val docElemId = artifact.sigmatics.toString

        // calculate a partition ID:
        // every entry with the same 'document id' (means doc elem id)
        // will be placed into the same partition
        val partitionId = MurmurHash3.stringHash(docElemId) % NUM_PARTITIONS

        // Schema:
        // PARTITON : layer!attribute : docElemId : (None)
        val mutation = new Mutation(partitionId.toString)
        mutation.put(layerAndAttribute, docElemId, "")
        mutation
      }

      // send mutations to accumulo
      indexWriter.addMutations(mutations.force.toIterable.asJava)
      log.info(s"(indexWriter) has written ${mutations.size} mutations.")
    }

  }

}
