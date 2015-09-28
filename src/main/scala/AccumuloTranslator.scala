package eu.themerius.docelemstore

import akka.actor.{ ActorRef, ActorSystem, Props, Actor, Inbox }

import scala.concurrent.duration._
import scala.xml.XML
import scala.xml.NodeSeq

case class FoundCorpus(xmlStr: String)
case class FoundDocelems(xml: NodeSeq)
case class FoundAnnotaitons(xml: NodeSeq)

class AccumuloTranslator extends Actor {

  def receive = {
    case FoundCorpus(xmlStr) => {
      // parsing xml
      val xml = XML.loadString(xmlStr)
      // split into docelems and annotations
      val docelems = xml \\ "corpus" \\ "docelems"
      val annots = xml \\ "corpus" \\ "annotations"
      // send to transform it to accumulo structures
      self ! FoundDocelems(docelems)
      self ! FoundAnnotaitons(annots)
    }

    case FoundDocelems(xml) => println("Found " + xml)
    case FoundAnnotaitons(xml) => println("Found " + xml)
  }

}
