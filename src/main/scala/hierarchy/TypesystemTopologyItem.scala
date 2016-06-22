package eu.themerius.docelemstore

import de.fraunhofer.scai.bio.extraction.types.text.CoreAnnotation
import de.fraunhofer.scai.bio.extraction.types.text.documentelement.meta.MetaElement
import de.fraunhofer.scai.bio.extraction.types.text.documentelement.structure.StructureElement


class TypesystemTopologyItem(val instance: CoreAnnotation) extends TopologyItem[CoreAnnotation] {

  var superordinate: TopologyItem[CoreAnnotation] = null
  var follows: TopologyItem[CoreAnnotation] = null
  var rank = instance.getBegin

  def getHierarchyPosition(item: TopologyItem[CoreAnnotation]) = {
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
