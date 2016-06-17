package eu.themerius.docelemstore


import org.scalatest._


class HierarchyUtilsSpec extends FlatSpec with Matchers {

  // Simple implementation of the interface

  case class DocElem(id: String, typ: String)

  class MyTopologyItem(val instance: DocElem) extends TopologyItem[DocElem] {

    var superordinate: TopologyItem[DocElem] = null
    var follows: TopologyItem[DocElem] = null
    var rank = -1

    override def getHierarchyPosition(item: TopologyItem[DocElem]) = {
      hierarchyMapping.getOrElse(item.instance.typ, 0)
    }

    override def toString = s"${instance.id}/${rank}"

  }

  /* SAMPLE HIERARCHY:

    0 Header (begin_offset=0)
      1 Section  (begin_offset=10)
        2 StructureElement (e.g. Paragraph)  (begin_offset=20)
        3 SubSection  (begin_offset=50)
          4 StructureElement (e.g. Paragraph) (begin_offset=60)
          5 StructureElement (e.g. Paragraph) (begin_offset=90)
      6 Section (begin_offset=100)
        7 StructureElement (e.g. Paragraph) (begin_offset=110)

  */

  val header = new DocElem("h1", "Header")
  val h1 = new MyTopologyItem(new DocElem("h1", "Header"))
  h1.superordinate = h1
  h1.rank = 0

  val s1 = new MyTopologyItem(new DocElem("s1", "Section"))
  s1.rank = 10
  val s2 = new MyTopologyItem(new DocElem("s2", "Section"))
  s2.rank = 100

  val ss1 = new MyTopologyItem(new DocElem("ss1", "SubSection"))
  ss1.rank = 50

  val se1 = new MyTopologyItem(new DocElem("se1", "StructureElement"))
  se1.rank = 20
  val se2 = new MyTopologyItem(new DocElem("se2", "StructureElement"))
  se2.rank = 60
  val se3 = new MyTopologyItem(new DocElem("se3", "StructureElement"))
  se3.rank = 90
  val se4 = new MyTopologyItem(new DocElem("se4", "StructureElement"))
  se4.rank = 110

  // The first item is the header! The point of reference for the topology
  val items = List(h1, s1, s2, ss1, se1, se2, se3, se4)

  "The HierarchyUtils" should "generate a list sorted by rank of TopologyItem" in {

    val utils = new HierarchyUtils(items)

    utils.rankSorted should equal (List(h1, s1, se1, ss1, se2, se3, s2, se4))

  }

  it should "guarantee that the first list item ('document header') is the point of reference for the topology" in {

    val utils = new HierarchyUtils(items)

    utils.checkedHead should equal (h1)
    utils.checkedHead.superordinate should equal (h1)
    utils.checkedHead.follows should equal (h1)

  }

  it should "have a specific hierarcy mapping (can compare)" in {

    // e.g. s1 has a lower hierarchy priority as h1
    s1 compare h1 should be (1)
    // e.g. s1 has a higher hierarchy priority as se1
    s1 compare se1 should be (-1)
    // e.g. se1 and se2 have the same hierarchy priority
    se1 compare se2 should be (0)

  }

  it should "enshure that each item follows it's _direct_ superordinate" in {

    val utils = new HierarchyUtils(items)

    val hierarchializedItems = utils.assignHierarchy

    hierarchializedItems(0).follows should equal (h1)
    hierarchializedItems(1).follows should equal (h1)
    hierarchializedItems(2).follows should equal (s1)
    hierarchializedItems(3).follows should equal (s1)
    hierarchializedItems(4).follows should equal (ss1)
    hierarchializedItems(5).follows should equal (ss1)
    hierarchializedItems(6).follows should equal (h1)
    hierarchializedItems(7).follows should equal (s2)

  }

}
