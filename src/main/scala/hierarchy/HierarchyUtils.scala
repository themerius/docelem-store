package eu.themerius.docelemstore


trait TopologyItem[DocElem] extends scala.math.Ordered[TopologyItem[DocElem]] {

  val instance: DocElem

  var superordinate: TopologyItem[DocElem] // esp. the header
  var follows: TopologyItem[DocElem]
  var rank: Int

  def hierarchyMapping = Map(
    "Header" -> 0,
    "Matter" -> 0, // as alternative to header. it's more a hack for SCAI typesystem!!
    "FrontMatter" -> 1,
    "BodyMatter" -> 1,
    "BackMatter" -> 1,
    "Part" -> 2,
    "Chapter" -> 3,
    "Section" -> 4,
    "SubSection" -> 5,
    "SubSubSection" -> 6,
    "MetaElement" -> 7,
    "StructureElement" -> 7
  )

  // Use here the hierarchy mappings!
  def getHierarchyPosition(item: TopologyItem[DocElem]): Int

  override def compare(that: TopologyItem[DocElem]): Int = {
    if (getHierarchyPosition(this) > getHierarchyPosition(that)) {
      1  // this should follow that
    } else if (getHierarchyPosition(this) < getHierarchyPosition(that)) {
      -1 // recursive: this should look if that.follows is okay
    } else {
      0 // this should also use that.follows
    }
  }

}


class HierarchyUtils[DocElem](items: Seq[TopologyItem[DocElem]]) {

  lazy val rankSorted = items.sortBy(_.rank)

  // the header is defined as a document element which is referencing itself as global superordinate
  lazy val checkedHead = {

    val head = rankSorted.head
    assume(head.superordinate == rankSorted.head)
    head.follows = rankSorted.head
    head

  }

  lazy val preparedItems = {

    checkedHead +: rankSorted.tail

  }

  def assginFollower(current: TopologyItem[DocElem], previous: TopologyItem[DocElem]): TopologyItem[DocElem] = {

    if (current > previous) {  // 1
      // current should follow previous
      current.follows = previous
      current
    } else if (current == previous) { // 0
      // current should use previous.follows
      current.follows = previous.follows
      current
    } else {  // current<previous thus -1
      // use previous.follows and check recursive
      assginFollower(current, previous.follows)
    }

  }

  def assignHierarchy: Seq[TopologyItem[DocElem]] = {

    checkedHead +: preparedItems.sliding(2).map{ docElemPair =>
      val previous = docElemPair(0)
      val current = docElemPair(1)
      assginFollower(current, previous)
    }.toSeq

  }

}
