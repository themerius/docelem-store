package eu.themerius.docelemstore

import de.fraunhofer.scai.bio.extraction.types.text.documentelement.DocumentElement
import de.fraunhofer.scai.bio.extraction.types.text.documentelement.meta.MetaElement
import de.fraunhofer.scai.bio.extraction.types.text.documentelement.structure.StructureElement


class TypesystemTopologyItem(val instance: DocumentElement) extends TopologyItem[DocumentElement] {

  var superordinate: TopologyItem[DocumentElement] = null
  var follows: TopologyItem[DocumentElement] = null
  var rank = instance.getBegin

  def getHierarchyPosition(item: TopologyItem[DocumentElement]) = {
    if (item.instance.isInstanceOf[StructureElement]) {
      hierarchyMapping.getOrElse("StructureElement", 100)
    } else if (item.instance.isInstanceOf[MetaElement]) {
      hierarchyMapping.getOrElse("MetaElement", 100)
    } else {
      hierarchyMapping.getOrElse(item.instance.getClass.getSimpleName, 0)
    }
  }

  override def toString = s"${instance.getClass.getSimpleName}/$rank"

}
