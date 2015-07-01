package eu.themerius.docelemstore

import akka.actor.{ ActorRef, ActorSystem, Props, Actor, Inbox }

case class Get(uuid: String, version: Int = 0)
case class Create(dep: Seq[DocElemPayload])
case class GetFlatTopology(uuid: String)
case class Init(fileName: String)

case class Response(de: List[ActorRef])

class Store extends Actor {
  override def preStart() = {
    OrientDB
  }

  def receive = {
    case Get(uuid, version) => {
      println(s"Get and Create § $uuid in version $version.")
      val payload = OrientDB.fetchDocElemPayload(uuid)
      val de = context.actorOf( Props(classOf[DocElem], payload) )
      sender ! Response(List(de))
    }
    case Create(deps) => {
      OrientDB.saveDocElemPayloads(deps)
      // if error send error-response?
    }
    case GetFlatTopology(uuid) => {
      println(s"Get flat topo for § $uuid.")
      val payload = OrientDB.fetchDocElemPayload(uuid)
      // should calculate topology
      val lst = List.fill(10)( context.actorOf(Props(classOf[DocElem], payload)) )  // TODO: getOrCreate
      sender ! Response(lst)
    }
    case Init(fileName) => {
      println("Init Database with some data")
      val reader = new XMLReader(getClass.getResource(fileName).getPath)
      val payloads = reader.getDocElemPayload
      OrientDB.saveDocElemPayloads(payloads)
    }
    case other => println("Can't handle " + other)
  }
}

case class Projection(p: String)
case class AnnotateWith(uuid: String)
case class OfProvenance(uuid: String)

class DocElem(payload: DocElemPayload) extends Actor {
  def receive = {
    case Projection(p) => p match {
      case "Html" => sender ! getHtmlProjection
      case "Text" => sender ! payload.model // something like toString, our fallback
      case "Raw" => sender ! payload.model // isTextModel, isBinaryModel
      case other => sender ! "No Projection found"
    }
    case AnnotateWith(uuid) => OrientDB.annotatedWith(payload.uuid, uuid)
    case OfProvenance(uuid) => OrientDB.hasProvanance(payload.uuid, uuid)
    // case Update
    case other => {
      println("Can't handle " + other)
      sender ! "Can't handle " + other
    }
  }

  def getHtmlProjection = {
    val templateUuid = getHtmlProjectionMapping(payload.typ)
    val templatePayload = OrientDB.fetchDocElemPayload(templateUuid)

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
