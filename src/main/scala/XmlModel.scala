package eu.themerius.docelemstore

import scala.xml.XML
import scala.xml.Elem
import scala.xml.PrettyPrinter


trait XmlModel extends Model {

  var xml: Elem = _
  // width is 80 characters and indent is 2 spaces
  val gen = new PrettyPrinter(80, 2)

  def deserialize(m: Array[Byte]) = {
    xml = XML.loadString(new String(m, "UTF-8"))
    this
  }

  def serialize: Array[Byte] = gen.format(xml).getBytes

}

trait XmlSingleDocElemQueryBuilder extends XmlModel with QueryBuilder {

  def buildQuery = {
    val query = (xml \\ "meta").filter( _ match {
      case m @ <meta /> if (m \ "@name").text == "docelem-id" => true
      case _ => false
    })
    // TODO: check if query is really valid
    // TODO: specify <undefined cause="..." /> for all projects! Also suitable for log entries? Or something like a error-docelem/corpus?
    if (query.nonEmpty)
      Query(QueryTarget.SingleDocElem, <query>{query}</query>)
    else
      Query(QueryTarget.Invalid, <query><undefined /></query>)
  }

}
