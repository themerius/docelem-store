package eu.themerius.docelemstore

import akka.actor.{ Actor, ActorRef }
import akka.event.Logging

import org.apache.accumulo.core.data.Mutation
import org.apache.accumulo.core.client.BatchWriterConfig

import scala.util.hashing.MurmurHash3
import scala.collection.JavaConverters._

import java.net.URI

import QueryTarget.SingleDocElem

case class BuildQuery(builder: QueryBuilder, data: Array[Byte], reply: Reply)
case class Scan(query: Query, reply: Reply)
case class PrepareReply(corpus: Corpus, reply: Reply)

class AccumuloQueryer extends Actor {

  val log = Logging(context.system, this)

  def receive = {

    case BuildQuery(builder, data, reply) => {
      builder.deserialize(data)
      val query = builder.buildQuery
      log.debug(query.toString)
      self ! Scan(query, reply)
    }

    case Scan(Query(SingleDocElem, queryXml), reply) => {
      // TODO: implement
      self ! PrepareReply(null, reply)
      log.info(s"(Query SingleDocElem) found ${0} artifacts.")
    }

    case PrepareReply(corpus, reply) => {
      // TODO: refactor Reply(answer, channel, trackingNr). create extra channel class which can test if the cannel string is valid?
      context.parent ! Reply("", reply.to, reply.trackingNr)
      log.info(s"(Reply) send to ${reply.to}.")
    }
  }

}
