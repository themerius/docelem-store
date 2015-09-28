package eu.themerius.docelemstore

import akka.actor.{ ActorRef, ActorSystem, Props, Actor, Inbox }

import scala.concurrent.duration._
import scala.xml.XML
import scala.xml.NodeSeq
import scala.util.hashing.MurmurHash3

import org.apache.accumulo.core.data.Mutation

case class FoundCorpus(xmlStr: String)
case class FoundDocelems(xml: NodeSeq)
case class FoundAnnotaitons(xml: NodeSeq)

class AccumuloTranslator extends Actor {

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

        println(hash, authority, uid, model.text)

        (mutation, mutationTM)
      }
      // TODO send to accumulo database actor
      println(mutations)
    }

    case FoundAnnotaitons(xml) => println("Found " + xml)
  }

}
