package eu.themerius.docelemstore

import akka.actor.{ ActorRef, ActorSystem, Props, Actor, Inbox, PoisonPill }
import akka.pattern.gracefulStop
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

case class Get(uuid: String, version: Int = 0)
case class GetSimple(uuid: String, version: Int = 0)
case class Create(dep: Seq[DocElemPayload])
case class GetOrCreate(dps: Seq[DocElemPayload])
case class GetOrCreate2(dps: Seq[DocElemPayload])
case class GetFlatTopology(uuid: String)
case class Init(fileName: String)

case class Annotate(uuid: String, payload: DocElemPayload, purpose: String)

case class Response(de: List[ActorRef])
case class ResponsePayload(deps: Seq[DocElemPayload])

case class FoundBatchOf(dps: Seq[DocElemPayload], annots: Seq[Annotate])

class Store extends Actor {
  override def preStart() = {
    Accumulo
  }

  def receive = {
    case Get(uuid, version) => {
      println(s"Get and Create DocElem-Actor § $uuid in version $version.")
      val payload = Accumulo.fetchDocElemPayload(uuid)
      val de = context.actorOf( Props(classOf[DocElem], payload) )
      context.watch(de)
      sender ! Response(List(de))
    }
    case GetSimple(uuid, version) => {
      println(s"Get Payload § $uuid in version $version.")
      val payload = Accumulo.fetchDocElemPayload(uuid)
      sender ! ResponsePayload(List(payload))
    }
    case Create(deps) => {
      Accumulo.saveDocElemPayloads(deps)
      // if error send error-response?
    }
    case GetOrCreate(deps) => {
      val correctedPayloads = Accumulo.saveDocElemPayloads(deps)
      sender ! ResponsePayload(correctedPayloads)
    }
    case GetOrCreate2(deps) => {
      val correctedPayloads = Accumulo.saveDocElemPayloads(deps)
      val de = context.actorOf( Props(classOf[DocElem], correctedPayloads.head) )
      context.watch(de)
      sender ! Response(List(de))
    }
    case Annotate(uuid, payload, purpose) => {
      val correctedPayload = Accumulo.saveDocElemPayloads(List(payload)).head
      val ids = Annotation.ConnectedVs(uuid, correctedPayload.uuid)
      val sem = Annotation.Semantics(purpose, "debug", Map[String, Any]())
      Accumulo.annotate(ids, sem, Map[String, Any]())
    }
    case FoundBatchOf(dps, annots) => Future {
      Accumulo.saveDocElemPayloads(dps)
      val annotsSet = annots.toSet.toSeq
      Accumulo.saveDocElemPayloads(annotsSet.map(_.payload))
      annotsSet.foreach{ ann =>
        val ids = Annotation.ConnectedVs(ann.uuid, ann.payload.uuid)  // assuming the payload.uuids must not be corrected. #TODO:0 make more general!
        val sem = Annotation.Semantics(ann.purpose, "debug", Map[String, Any]())
        Accumulo.annotate(ids, sem, Map[String, Any]())
      }
    }
    case GetFlatTopology(uuid) => {
      println(s"Get flat topo for § $uuid.")
      val payload = Accumulo.fetchDocElemPayload(uuid)
      // should calculate topology
      val lst = List.fill(10)( context.actorOf(Props(classOf[DocElem], payload)) )  // #TODO:10 getOrCreate
      sender ! Response(lst)
    }
    case Init(fileName) => {
      println("Init Database with some data")
      val reader = new XMLReader(getClass.getResource(fileName).getPath)
      val payloads = reader.getDocElemPayload
      Accumulo.saveDocElemPayloads(payloads)
    }
    case "DieHard" => {
      stopChilds()
      self ! PoisonPill
    }
    case other => println("Can't handle " + other)
  }

  def stopChilds() = {
    val stopped = context.children.map { child =>
      context.unwatch(child)
      gracefulStop(child, 5.seconds, PoisonPill)
    }.toList
    // block until all actors are gracefully stopped or have a timeout
    println(s"Store is stopping. Await for ${stopped.length} childrens to stop.")
    val awaited = stopped.map(Await.result(_, 5.seconds))
    println(s"Store has gracefully stopped all children: ${awaited.min}.")
  }
}

case class Projection(p: String)
case class AnnotateWith(uuid: String, purpose: String)
case class AnnotateWithPayload(payload: DocElemPayload, purpose: String)

class DocElem(payload: DocElemPayload) extends Actor {
  def receive = {
    case Projection(p) => p match {
      case "Html" => sender ! getHtmlProjection
      case "Text" => sender ! payload.model // something like toString, our fallback
      case "Raw" => sender ! payload.model // isTextModel, isBinaryModel
      case other => sender ! "No Projection found"
    }
    case AnnotateWith(uuid, purpose) => {
      val ids = Annotation.ConnectedVs(payload.uuid, uuid)
      val sem = Annotation.Semantics(purpose, "debug", Map[String, Any]())
      Accumulo.annotate(ids, sem, Map[String, Any]())
      Accumulo.fetchAllAnnotations
    }
    case AnnotateWithPayload(otherPayload, purpose) => {
      val correctedPayload = Accumulo.saveDocElemPayloads(List(otherPayload)).head
      val ids = Annotation.ConnectedVs(payload.uuid, correctedPayload.uuid)
      val sem = Annotation.Semantics(purpose, "debug", Map[String, Any]())
      Accumulo.annotate(ids, sem, Map[String, Any]())
    }
    // case Edit
    case other => {
      println("Can't handle " + other)
      sender ! "Can't handle " + other
    }
  }

  def getHtmlProjection = {
    val templateUuid = getHtmlProjectionMapping(payload.typ)
    val templatePayload = Accumulo.fetchDocElemPayload(templateUuid)

    val tmplXml = scala.xml.XML.loadString(templatePayload.model)

    // Load Model/Attributes of the Target DocumentElement
    var attrs = Map("model" -> payload.model)

    // Apply Template on the Target
    val tmplXmlComplete = new scala.xml.transform.RewriteRule {
       override def transform(n: scala.xml.Node): Seq[scala.xml.Node] = n match {
         case <ref>{key}</ref> => <span>{scala.xml.Unparsed(attrs(key.text))}</span>
         case elem: scala.xml.Elem => elem copy (child = elem.child flatMap (this transform))
         case other => other
       }
     } transform tmplXml

     tmplXmlComplete(0).child.mkString
  }

  // type mapped onto uuid
  def getHtmlProjectionMapping (tpe: String) = tpe match {
    case "Paragraph" => "templ-001"
    case "Section" => "templ-002"
    case other => "templ-001"
  }

}
