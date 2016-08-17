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


case class DocElem2Accumulo(corpus: Corpus)
case class Add2AnnotationIndex(corpus: Corpus)
case object UpdateLocalityGroups

class AccumuloFeeder extends Actor {

  val log = Logging(context.system, this)

  val NUM_PARTITIONS = 32

  val configWriter = new BatchWriterConfig
  val artifactsWriter = AccumuloConnectionFactory.get.createBatchWriter(AccumuloConnectionFactory.ARTIFACTS, configWriter)
  val indexWriter = AccumuloConnectionFactory.get.createBatchWriter(AccumuloConnectionFactory.SEMANTIC_INDEX, configWriter)

  context.system.scheduler.scheduleOnce(10.minutes) {
    // Because updating the locality groups is a slow process,
    // we schedule that all x minutes.
    self ! UpdateLocalityGroups
  }

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

        addLocalityGroup(artifact)

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
      //artifactsWriter.flush
      log.info(s"(artifactsWriter) has written ${mutations.size} mutations.")
    }

    case Add2AnnotationIndex(corpus: Corpus) => {
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
      //indexWriter.flush
      log.info(s"(indexWriter) has written ${mutations.size} mutations.")
    }

    case UpdateLocalityGroups => {
      setLocalityGroups
    }

  }

  val otherThanAlphaOrDigits = """[^\p{Alpha}\p{Digit}]+""".r
  var localityGroups = Map[String, Set[Text]]()

  def addLocalityGroup(artifact: KnowledgeArtifact) = {
    // Every document (topology) header should grouped by ColumnFamily, so Accumulo can scan fast for that topology. The can change on runtime, see Accumulo Book on page 138.
    // Note: this will need a compactation phase to apply!
    if (artifact.meta.specification.toString.startsWith("topo")) {
      val docHeader = artifact.pragmatics.toString  // pragmatics is stored at column familiy in Accumulo
      val groupName = otherThanAlphaOrDigits.replaceAllIn(docHeader.split("@tag:").head, "-")
      val columnFamily = new Text(docHeader)
      // Every doccument header (= col familiy) forms a locality group
      val setWithin = localityGroups.getOrElse(groupName, Set())
      localityGroups = localityGroups.updated(groupName, setWithin ++ Set(columnFamily))
    }
  }

  def setLocalityGroups = {
    if (localityGroups.nonEmpty) {
      val localGrpJavaMap = localityGroups.map( t => (t._1, t._2.asJava) ).asJava
      AccumuloConnectionFactory.ops.setLocalityGroups(AccumuloConnectionFactory.ARTIFACTS, localGrpJavaMap)
      log.info(s"Adding $localityGroups to Accumulo Table's LocalityGroups.")
      localityGroups = Map[String, Set[Text]]()
    }
  }

}
