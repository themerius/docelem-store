package eu.themerius.docelemstore

import akka.actor.{ Actor }
import akka.event.Logging

import org.apache.accumulo.core.security.Authorizations
import org.apache.accumulo.core.data.Range

import scala.collection.JavaConverters._
import scala.xml.PrettyPrinter

import java.net.URI
import java.lang.Long

import QueryTarget.SingleDocElem

case class BuildQuery(builder: QueryBuilder, data: Array[Byte], reply: Reply)
case class Scan(query: Query, reply: Reply)
case class PrepareReply(corpus: Corpus, reply: Reply)

// TODO: rename to AccumuloRetrieval?
class AccumuloQueryer extends Actor {

  val log = Logging(context.system, this)
  val xmlGenerator = new PrettyPrinter(80, 2)
  val version = s"${BuildInfo.name}:${BuildInfo.version}"

  def receive = {

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
  }

  def scanSingleDocelem(uri: URI): Corpus = {
    val auths = new Authorizations()
    val scan = AccumuloConnectionFactory.get.createScanner("knowledge_artifacts_dev", auths)
    scan.setRange(Range.exact(uri.toString))
    //scan.fetchColumn(new Text(typ), new Text(uid))

    val artifacts =
      for (entry <- scan.asScala) yield {
        val key = entry.getKey()
        val value = entry.getValue()

        val qual = key.getColumnQualifier.toString
        val (semantics, spec, fingerprint) = qual.split("\u0000") match {
          case Array(semantics, spec, fingerprint) => (semantics, spec, fingerprint)
          case _ => ("", "", "0")
        }

        KnowledgeArtifact(
          new URI(key.getRow.toString),
          new URI(key.getColumnFamily.toString),
          new URI(semantics),
          value.get,
          Meta(new URI(spec), Long.parseLong(fingerprint, 16).toInt)
        )
      }

    Corpus(artifacts.toSeq)
  }

}
