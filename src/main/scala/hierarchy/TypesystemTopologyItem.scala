package eu.themerius.docelemstore

import de.fraunhofer.scai.bio.extraction.types.text.documentelement.DocumentElement
import de.fraunhofer.scai.bio.extraction.types.text.documentelement.meta.MetaElement
import de.fraunhofer.scai.bio.extraction.types.text.documentelement.structure.StructureElement


class TypesystemTopologyItem(val instance: DocumentElement) extends TopologyItem[DocumentElement] {

  var superordinate: TopologyItem[DocumentElement] = null
  var follows: TopologyItem[DocumentElement] = null
  var rank = -1

  def getHierarchyPosition(item: TopologyItem[DocumentElement]) = {
    if (item.isInstanceOf[StructureElement]) {
      hierarchyMapping.getOrElse("StructureElement", 0)
    } else if (item.isInstanceOf[MetaElement]) {
      hierarchyMapping.getOrElse("MetaElement", 0)
    } else {
      hierarchyMapping.getOrElse(item.getClass.getName, 0)
    }
  }

}
