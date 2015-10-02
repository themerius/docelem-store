package eu.themerius.docelemstore

import akka.actor.{ ActorRef, ActorSystem, Props, Actor, Inbox }

import scala.concurrent.duration._
import scala.xml.XML
import scala.xml.NodeSeq
import scala.util.hashing.MurmurHash3

// Better to use Converters. See: http://stackoverflow.com/questions/8301947
import scala.collection.JavaConverters._

import org.apache.accumulo.core.data.Mutation
import org.apache.accumulo.core.security.ColumnVisibility

import org.apache.accumulo.core.security.Authorizations
import org.apache.accumulo.core.data.Range
import org.apache.hadoop.io.Text

case class FoundCorpus(xmlStr: String)
case class FoundDocelems(xml: NodeSeq)
case class FoundAnnotaitons(xml: NodeSeq)

case class QueryDocelem(queryStr: String, replyTo: String)

class AccumuloTranslator extends Actor {

  val storage = context.actorSelection("/user/accumulo-storage")

  def receive = {
    case FoundCorpus(xmlStr) => {
      // parsing xml
      val xml = XML.loadString(xmlStr)
      // split into docelems and annotations
      val docelems = xml \\ "docelem"
      val annots = xml \\ "annotation"
      // send to transform it to accumulo structures
      self ! FoundDocelems(docelems)
      self ! FoundAnnotaitons(annots)
    }

    case FoundDocelems(xml) => {
      val mutations = xml.map { docelem =>
        val uiid = docelem \\ "uiid"
        val (authority, typ, uid) = uiid.text.split("/") match {
          case Array(authority, typ, uid) => (authority, typ, uid)
          case _ => ("undefiend", "undefined", "undefined")
        }
        val model = docelem \\ "model"
        val hash = MurmurHash3.stringHash(model.text).toString  // Use BigInt toByteArray?
        val typeUid = s"${typ}/${uid}"

        // Optional:
        // val colVis = new ColumnVisibility("public")
        // val timestamp = System.currentTimeMillis()

        val mutation = new Mutation(hash.getBytes)  // hash equals row id
        mutation.put(authority.getBytes, typeUid.getBytes, model.text.getBytes)

        val mutationTM = new Mutation(authority.getBytes)
        mutationTM.put(typ.getBytes, uid.getBytes, hash.getBytes)

        (mutation, mutationTM)
      }
      // Send to accumulo database actor
      val dedupes = mutations.map(_._1).toIterable.asJava
      val versions = mutations.map(_._2).toIterable.asJava
      storage ! WriteDocelems(dedupes, versions, mutations.size)
    }

    case FoundAnnotaitons(xml) => {
      val mutations = xml.map { annot =>
        val from = (annot \ "from").text
        val fromVersion = (annot \ "from" \ "@version").text
        val to = (annot \ "to").text
        val layer = (annot \ "@layer").text
        val purpose = (annot \ "@purpose").text
        val position = (annot \ "@position").text
        //val layerProps = (annot \ layer).text

        val rowId = s"$from/$fromVersion".getBytes
        val colVis = new ColumnVisibility()
        val annotHash = MurmurHash3.stringHash(to+layer+purpose+position).toLong
        // TODO also include layerProps into annotation version hash

        val mutation = new Mutation(rowId)
        mutation.put(layer.getBytes, purpose.getBytes, colVis, annotHash, annot.toString.getBytes)
        mutation
      }
      // Send to accumulo database actor
      val annots = mutations.toIterable.asJava
      storage ! WriteAnnotations(annots, mutations.size)
    }

    case QueryDocelem(queryStr, replyTo) => {
      // prepare scan
      val (authority, typ, uid) = queryStr.split("/") match {
        case Array(authority, typ, uid) => (authority, typ, uid)
        case _ => ("undefiend", "undefined", "undefined")
      }
      // For fetching the hash of the newest version
      storage ! FindDocelem(authority, typ, uid, replyTo, sender())
    }
  }

}
