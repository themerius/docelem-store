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

case class QueryDocelem(queryStr: String, replyTo: String, trackingNr: String)
case class QueryAnnotationIndex(queryStr: String, replyTo: String, trackingNr: String)

class AccumuloTranslator extends Actor {

  //val storage = context.actorSelection("/user/accumulo-storage")
  val storage = context.actorOf(Props[AccumuloStorage])

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
        // Preserve also the models inner xml strucutre
        val model = (docelem \\ "model").toString.replace("<model>", "").replace("</model>", "")
        val hash = MurmurHash3.stringHash(model).toString  // Use BigInt toByteArray?
        val typeUid = s"${typ}/${uid}"

        // Optional:
        // val colVis = new ColumnVisibility("public")
        // val timestamp = System.currentTimeMillis()

        val mutation = new Mutation(hash.getBytes)  // hash equals row id
        mutation.put(authority.getBytes, typeUid.getBytes, model.getBytes)

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
        val annotHash = MurmurHash3.stringHash(to+layer+purpose+position).toString
        // TODO also include layerProps into annotation version hash

        val purposeHash = s"$purpose#$annotHash"

        val mutation = new Mutation(rowId)
        mutation.put(layer.getBytes, purposeHash.getBytes, colVis, annot.toString.getBytes)
        mutation
      }

      val mutationsIndex = xml.map { annot =>
        val from = (annot \ "from").text
        val to = (annot \ "to").text
        val layer = (annot \ "@layer").text
        val purpose = (annot \ "@purpose").text

        val rowId = layer.getBytes
        val colVis = new ColumnVisibility()

        val mutation = new Mutation(rowId)
        mutation.put(to.getBytes, from.getBytes, colVis, "".getBytes)
        mutation.put(s"${purpose}://${to}".getBytes, from.getBytes, colVis, "".getBytes)
        mutation
      }
      // Send to accumulo database actor
      val annots = mutations.toIterable.asJava
      val index = mutationsIndex.toIterable.asJava
      storage ! WriteAnnotations(annots, index, mutations.size)
    }

    case QueryDocelem(queryStr, replyTo, trackingNr) => {
      // prepare scan
      val (authority, typ, uid) = queryStr.split("/") match {
        case Array(authority, typ, uid) => (authority, typ, uid)
        case _ => ("undefiend", "undefined", "undefined")
      }
      // For fetching the hash of the newest version
      storage ! FindDocelem(authority, typ, uid, replyTo, trackingNr, sender())
    }

    case QueryAnnotationIndex(queryStr, replyTo, trackingNr) => {
      val xml = scala.xml.XML.loadString(queryStr)
      val searchedLayers = (xml \ "layer").map(_.text).map(_.trim)
      val searchedUiids = (xml \ "annot").map(_.text).map(_.trim)
      storage ! DiscoverDocelemsWithAnnotations(searchedLayers, searchedUiids, replyTo, trackingNr, sender())
    }
  }

}
