package eu.themerius.docelemstore

import akka.actor.{ Actor }
import akka.event.Logging

import scala.util.hashing.MurmurHash3
import scala.collection.JavaConverters._

import scala.xml.PrettyPrinter
import scala.collection.mutable.TreeSet

import java.net.URI

import eu.themerius.docelemstore.utils.Stats.time

import QueryTarget.SingleDocElem
import QueryTarget.Topology
import QueryTarget.TopologyOnlyHierarchy


class InMemoryStore extends Actor {

  val log = Logging(context.system, this)
  val xmlGenerator = new PrettyPrinter(80, 2)
  val version = s"${BuildInfo.name}:${BuildInfo.version}"

  var WAL = Set.empty[KnowledgeArtifact]  // TODO: use TreeSet?

  def receive = {

    // TODO: extract Transform2DocElem into an dedicated Actor??
    case Transform2DocElem(model, data) => {

      // Transform a (domain specific) model into a DocElem-Corpus.
      model.deserialize(data)
      val corpus = model.applyRules

      // Write it to the write ahead log (set)
      WAL = WAL ++ corpus.artifacts.map { artifact =>
        KnowledgeArtifact(
          artifact.sigmatics,
          artifact.pragmatics,
          artifact.semantics,
          artifact.model,
          Meta(artifact.meta.specification, 0, 0L)  // TODO: useful refactoring for usage of fingerprint and timestamp...
        )
      }

      log.info(s"(WAL) swallows ${corpus.artifacts.size} artifacts.")

    }

    case BuildQuery(builder, data, reply) => {
      builder.deserialize(data)
      val query = builder.buildQuery
      log.debug(query.toString)
      self ! Scan(query, reply)
    }

    case Scan(Query(SingleDocElem, queryXml), reply) => {
      val text = (queryXml \\ "meta" \ "@content")(0).text
      val uri = new URI(text)
      log.info(s"(Query/SingleDocElem) scanning for ${uri}.")

      val corpus = scanSingleDocelem(uri)
      self ! PrepareReply(corpus, reply)
      log.info(s"(Query/SingleDocElem) found ${corpus.artifacts.size} artifacts.")
    }

    case Scan(Query(Topology, queryXml), reply) => {
      val text = (queryXml \\ "meta" \ "@content")(0).text
      val uri = new URI(text)
      log.info(s"(Query/Topology) scanning for ${uri}.")

      val corpus = scanTopology(uri)
      self ! PrepareReply(corpus, reply)
      log.info(s"(Query/Topology) found ${corpus.artifacts.size} artifacts.")
    }

    case Scan(Query(TopologyOnlyHierarchy, queryXml), reply) => {
      val text = (queryXml \\ "meta" \ "@content")(0).text
      val uri = new URI(text)
      log.info(s"(Query/TopologyOnlyHierarchy) scanning for ${uri}.")

      val xmlTopo = scanTopologyOnlyHierarchy(uri)
      self ! PrepareReplyOnlyHierarchy(xmlTopo, reply)
      log.info(s"(Query/TopologyOnlyHierarcy) found ${(xmlTopo \ "docelem").size} elements.")
    }

    case PrepareReply(corpus, reply) => {
      // TODO: refactor Reply(answer, channel, trackingNr). create extra channel class which can test if the cannel string is valid?

      // Map[URI,Seq]: docelem -> Seq(KnowledgeArtifact)
      val docelems = corpus.artifacts.groupBy(_.sigmatics)
      // Map[URI, Map[URI, Seq]]: docelem -> layer -> Seq(KnowledgeArtifact)
      val layers = docelems.map{case (k,v) => k -> v.groupBy(_.pragmatics)}

      // TODO: this needs some clean code here... and put into extra method/class, which is also better for testing...
      // <corpus>
      //   <docelem>
      //      <layer>
      //        <attr><attr>...
      val xmlCorpus =
        <corpus by={version}>{layers.map(de => <docelem uri={de._1.toString}>{de._2.map(la => <layer uri={la._1.toString}>{la._2.map(ar => <attr uri={ar.semantics.toString} spec={ar.meta.specification.toString}>{scala.xml.PCData(new String(ar.model, "UTF-8"))}</attr>)}</layer>)}</docelem>)}</corpus>

      context.parent ! Reply(xmlGenerator.format(xmlCorpus), reply.to, reply.trackingNr)
      log.info(s"(Reply) send to ${reply.to}.")
    }

    // TODO: awww, nice hack!
    case PrepareReplyOnlyHierarchy(xmlTopology, reply) => {
      context.parent ! Reply(xmlGenerator.format(xmlTopology), reply.to, reply.trackingNr)
      log.info(s"(ReplyOnlyHierarchy) send to ${reply.to}.")
    }

  }

  def scanSingleDocelem(uri: URI): Corpus = time (s"InMemory:scanSingleDocelem($uri)") {

    val artifacts = WAL.filter(_.sigmatics == uri)
    Corpus(artifacts.toSeq)

  }

  def scanTopology(uri: URI): Corpus = time (s"InMemory:scanTopology($uri)") {

    val involvedArtifacts = WAL.filter(_.pragmatics == uri)

    val artifacts = for (involved <- involvedArtifacts) yield {
      val uri = involved.sigmatics
      WAL.filter(_.sigmatics == uri)
    }

    Corpus(artifacts.flatten.toSeq)

  }

  def scanTopologyOnlyHierarchy(uri: URI): scala.xml.Node = time (s"InMemory:scanTopologyOnlyHierarchy($uri)") {

    val involvedArtifacts = WAL.filter(_.pragmatics == uri)

    val docelems = for (involved <- involvedArtifacts) yield {
      <docelem uri={involved.sigmatics.toString} follows={involved.semantics.toString} rank={new String(involved.model)} />
    }

    <topology superordinate={uri.toString}>
      {docelems}
    </topology>

  }


}
