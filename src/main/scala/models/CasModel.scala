package eu.themerius.docelemstore

import java.io.ByteArrayInputStream
import java.net.URI

import org.apache.uima.jcas.cas.FSArray
import de.fraunhofer.scai.bio.uima.core.util.UIMATypeSystemUtils
import de.fraunhofer.scai.bio.uima.core.util.UIMAViewUtils
import de.fraunhofer.scai.bio.uima.core.deploy.AbstractDeployer
import de.fraunhofer.scai.bio.uima.core.provenance.ProvenanceUtils
import org.apache.uima.fit.util.JCasUtil
import de.fraunhofer.scai.bio.msa.util.MessageUtils
import de.fraunhofer.scai.bio.extraction.types.meta.Header
import de.fraunhofer.scai.bio.extraction.types.text.NormalizedNamedEntity
import de.fraunhofer.scai.bio.extraction.types.text.Sentence
import de.fraunhofer.scai.bio.extraction.types.text.NLPRelation
import de.fraunhofer.scai.bio.extraction.types.text.CoreAnnotation
import de.fraunhofer.scai.bio.extraction.types.text.documentelement.structure.Paragraph
import de.fraunhofer.scai.bio.extraction.types.text.documentelement.DocumentElement
import de.fraunhofer.scai.bio.extraction.types.text.documentelement.structure.List
import de.fraunhofer.scai.bio.extraction.types.text.documentelement.container.Matter
import de.fraunhofer.scai.bio.extraction.types.text.documentelement.container.FrontMatter
import de.fraunhofer.scai.bio.extraction.types.text.documentelement.container.Section
import de.fraunhofer.scai.bio.extraction.types.text.documentelement.container.SubSection
import de.fraunhofer.scai.bio.extraction.types.text.documentelement.container.Outline
import de.fraunhofer.scai.bio.extraction.types.text.documentelement.meta.DocumentTitle
import de.fraunhofer.scai.bio.extraction.types.text.documentelement.meta.Abstract
import de.fraunhofer.scai.bio.extraction.types.meta.Person
import org.apache.uima.cas.CAS

import scala.util.hashing.MurmurHash3
import scala.collection.JavaConverters._



trait CasModel extends Model with Helper {

  var cas: CAS = _

  def jcas = cas.getJCas()
  def header = UIMAViewUtils.getHeaderFromView(jcas)

  def view = UIMAViewUtils.getOrCreatePreferredView(jcas, AbstractDeployer.VIEW_DOCUMENT)

  def layerUri = {
    ProvenanceUtils.getDocumentCollectionName(jcas)
  }

  def sigmaticUri = {

    val userSuppliedId = ProvenanceUtils.getUserSuppliedID(jcas)
    val documentConcept = header.getDocumentConcept

    var label = s"user-supplied-id:$userSuppliedId"

    if (documentConcept != null) {

      if (documentConcept.getPrefLabel != null) {
        label = Option(documentConcept.getPrefLabel.getValue).getOrElse(userSuppliedId)
      }

    }

    s"header/${label}"

  }

  def headerSource = {

    val documentConcept = header.getDocumentConcept
    var source = "?"

    if (documentConcept != null) {
      source = Option(documentConcept.getIdentifierSource).getOrElse("?")
    }

    source

  }

  def headerRawId = {

    val userSuppliedId = ProvenanceUtils.getUserSuppliedID(jcas)
    val documentConcept = header.getDocumentConcept
    var id = userSuppliedId

    if (documentConcept != null) {
      id = Option(documentConcept.getIdentifier).getOrElse(userSuppliedId)
    }

    id

  }

}

class XCasModel extends CasModel {

  def deserialize(m: Array[Byte]) = {
    val stream = new ByteArrayInputStream(m)
    cas = UIMATypeSystemUtils.getFilledCasFromSource("/SCAITypeSystem.xml", "/SCAIIndexCollectionDescriptor.xml", stream)
    this
  }

  def serialize: Array[Byte] = throw new Exception("not implemented yet")

}

class GzippedXCasModel extends CasModel {

  def deserialize(m: Array[Byte]) = {
    val uncompressed = MessageUtils.uncompressString(new String(m, "UTF-8"))
    val stream = new ByteArrayInputStream(uncompressed.getBytes("UTF-8"))
    cas = UIMATypeSystemUtils.getFilledCasFromSource("/SCAITypeSystem.xml", "/SCAIIndexCollectionDescriptor.xml", stream)
    this
  }

  def serialize: Array[Byte] = throw new Exception("not implemented yet")

}

trait Helper {

  def camelToUnderscores(name: String) = "[A-Z\\d]".r.replaceAllIn(name, {m =>
    if(m.end(0) == 1){
      m.group(0).toLowerCase()
    }else {
      "-" + m.group(0).toLowerCase()
    }
  })

  def uri(docelem: CoreAnnotation) = {
    val typeId = camelToUnderscores(docelem.getClass.getSimpleName)

    if (docelem.isInstanceOf[Outline]) {
      val outline = docelem.asInstanceOf[Outline]
      val title = Option(outline.getTitle).getOrElse("")
      val rhetorical = Option(outline.getRhetorical).getOrElse("")
      val hash = MurmurHash3.stringHash(title + rhetorical + docelem.getCoveredText)
      val hashHex = Integer.toHexString(hash)
      s"$typeId/murmur3:${hashHex}"
    } else {
      val hash = MurmurHash3.stringHash(docelem.getCoveredText)
      val hashHex = Integer.toHexString(hash)
      s"$typeId/murmur3:${hashHex}"
    }

  }

  def uri(par: Paragraph) = {
    val hash = MurmurHash3.stringHash(par.getCoveredText)
    val hashHex = Integer.toHexString(hash)
    s"paragraph/murmur3:${hashHex}"
  }

  def uri(list: List) = {
    val hash = MurmurHash3.stringHash(list.getCoveredText)
    val hashHex = Integer.toHexString(hash)
    s"list/murmur3:${hashHex}"
  }

  def uri(sen: Sentence) = {
    val hash = MurmurHash3.stringHash(sen.getCoveredText)
    val hashHex = Integer.toHexString(hash)
    s"sentence/murmur3:${hashHex}"
  }

  def dictId(nne: NormalizedNamedEntity) =
    nne.getConcept.getIdentifierSource.replace(".syn", "").toLowerCase

  def conceptId(nne: NormalizedNamedEntity) =
    nne.getConcept.getIdentifier

  def prefName(nne: NormalizedNamedEntity) =
    nne.getConcept.getPrefLabel.getValue.replaceAll("\\s", "_").toLowerCase

  def uri(nne: NormalizedNamedEntity) = {
    s"concept/${dictId(nne)}:${prefName(nne)}"
  }

  def uri(fm: FrontMatter) = {
    val hash = MurmurHash3.stringHash(fm.getCoveredText)
    val hashHex = Integer.toHexString(hash)
    s"front-matter/murmur3:${hashHex}"
  }

  def uri(dt: DocumentTitle) = {
    val hash = MurmurHash3.stringHash(dt.getCoveredText)
    val hashHex = Integer.toHexString(hash)
    s"document-title/murmur3:${hashHex}"
  }

  def uri(abs: Abstract) = {
    val hash = MurmurHash3.stringHash(abs.getCoveredText)
    val hashHex = Integer.toHexString(hash)
    s"abstract/murmur3:${hashHex}"
  }
}

trait ExtractRelations extends CasModel with ModelTransRules  {

  def relations(superordinate: Sentence) = JCasUtil.subiterate(view, classOf[NLPRelation], superordinate, true, false).iterator.asScala.map(sent => (sent, superordinate)).toList

  def nneMembers(rel: NLPRelation) = rel.getMembersAsArrayList.asScala.filter(member => member.getEntity.isInstanceOf[NormalizedNamedEntity])

  def genSearchArtifact(rel: NLPRelation, sen: Sentence) = {
    nneMembers(rel).map{ member =>
      val nne = member.getEntity.asInstanceOf[NormalizedNamedEntity]
      KnowledgeArtifact(
        new URI(uri(sen)),
        new URI(s"belief/$layerUri"),
        new URI(s"graph/${member.getRole.toLowerCase}/${dictId(nne)}:${prefName(nne)}"),
        "".getBytes,
        Meta(new URI("search-only"))
      )
    }
  }

  def genViewArtifacts(rel: NLPRelation, sen: Sentence, nofRels: Int) = {
    val forView = nneMembers(rel).map{ member =>
      val nne = member.getEntity.asInstanceOf[NormalizedNamedEntity]
      s"""{
        "begin": ${nne.getBegin - sen.getBegin},
        "end": ${nne.getEnd - sen.getBegin},
        "attr": "header/header",
        "ref": "concept/${dictId(nne)}:${prefName(nne)}"
      }"""
    }

    val posJson = s"""{
      "pos": [${forView.mkString(",")}]
    }""".getBytes

    KnowledgeArtifact(
      new URI(uri(sen)),
      new URI(s"belief/$layerUri"),
      new URI(s"bel/document:${nofRels}"),
      posJson,
      Meta(new URI("annotation@v1"), MurmurHash3.bytesHash(posJson))
    )
  }

  def genContentArtifacts(rel: NLPRelation, sen: Sentence, nofRels: Int) = {
    val bel = rel.getConcept.getPrefLabel.getValue.getBytes

    KnowledgeArtifact(
      new URI(uri(sen)),
      new URI(s"belief/$layerUri"),
      new URI(s"bel/document:${nofRels}"),
      bel,
      Meta(new URI("bel@v1.0"), MurmurHash3.bytesHash(bel))
    )
  }

}

trait ExtractParagraphs extends CasModel with ModelTransRules {

  def paragraphs = JCasUtil.iterator(view, classOf[Paragraph]).asScala.toList

  def genTopologyArtifact(par: Paragraph, nofParagraph: Int) = {
    KnowledgeArtifact(
      new URI(uri(par)),
      new URI(sigmaticUri),
      new URI(s"topo/${sigmaticUri}"),
      s"${(1 + nofParagraph) * 128}".getBytes,
      Meta(new URI("topo-rank@v1"))
    )
  }

}

trait ExtractGenericHierarchy extends CasModel with ModelTransRules with ExtractSentences {

  def docelems = JCasUtil.iterator(view, classOf[DocumentElement]).asScala.toList

  def headerDocElem = {
    val de = new Matter(jcas)  // as alternative to header, because header is in our typesystem no document element!
    de.setBegin(0)
    de
  }

  // Prepare Head of topology
  def topoHead = {
    val head = new TypesystemTopologyItem(headerDocElem)
    head.superordinate = head
    head
  }
  // Prepare the list of document elements
  def items = topoHead +: (docelems.map(new TypesystemTopologyItem(_)) ++ sentences.map(new TypesystemTopologyItem(_)))
  def hierarcyUtils = new HierarchyUtils(items)
  // Compute the hierarchy
  def hierarchizedDocelems = hierarcyUtils.assignHierarchy

  // TODO: remove HACK. We need for example a header document element!
  def uri_matter2header_HACK(docelem: CoreAnnotation) = {
    if(docelem.getClass.getSimpleName == "Matter") {
      sigmaticUri
    } else {
      uri(docelem)
    }
  }

  def genTopologyArtifact(docelem: TopologyItem[CoreAnnotation]) = {
    KnowledgeArtifact(
      new URI(uri_matter2header_HACK(docelem.instance)),
      new URI(sigmaticUri),
      new URI(s"topo/${uri_matter2header_HACK(docelem.follows.instance)}"),
      s"${docelem.rank}".getBytes,
      Meta(new URI("topo-rank@v1"))
    )
  }

  def addTopologyTag(topologyCorpus: Seq[KnowledgeArtifact]) = {

    val combinedHash = topologyCorpus.foldLeft(0) { (total, item) =>
      val data = s"${item.sigmatics}|${item.semantics}"
      // use hash from previous item as seed
      MurmurHash3.stringHash(data, total)
    }

    val combinedHashStr = Integer.toHexString(combinedHash)
    // append combinedHashStr as topology tag
    topologyCorpus.map(item => item.copy(pragmatics = new URI(s"${item.pragmatics}@tag:${combinedHashStr}")))

  }

}

trait ExtractLists extends CasModel with ModelTransRules {

  def lists = JCasUtil.iterator(view, classOf[List]).asScala.toList

  def genTopologyArtifact(list: List, nofLists: Int) = {
    KnowledgeArtifact(
      new URI(uri(list)),
      new URI(sigmaticUri),
      new URI(s"topo/${sigmaticUri}"),
      s"${(1 + nofLists) * 128}".getBytes,
      Meta(new URI("topo-rank@v1"))
    )
  }

  def genContentArtifact(list: List) = {
    // HACK: because uima's StringList sucks!!!
    var i = 0
    var buffer = Seq[String]()
    while (i >= 0) {
      try {
        val item = list.getItems.getNthElement(i)
        buffer = item +: buffer
        i = i + 1
      } catch {
        case e: org.apache.uima.cas.CASRuntimeException => i = -1
      }
    }

    val htmlSnippet = buffer.reverse.mkString("<li>", "</li><li>", "</li>")

    KnowledgeArtifact(
      new URI(uri(list)),
      new URI("_"),
      new URI(s"list/html"),
      htmlSnippet.getBytes,
      Meta(new URI("html-snippet"))
    )
  }

}

trait ExtractOutlines extends CasModel with ModelTransRules {

  def outlines = JCasUtil.iterator(view, classOf[Outline]).asScala.toList

  def getNumbering(sec: Outline) = {
    if (sec.getNumbering == null) {
      ""
    } else {
      var i = 0
      var buffer = Seq[String]()
      while (i >= 0) {
        try {
          val item = sec.getNumbering.getNthElement(i)
          buffer = item +: buffer
          i = i + 1
        } catch {
          case e: org.apache.uima.cas.CASRuntimeException => i = -1
        }
      }
      buffer.mkString(".")
    }
  }

  def genContentArtifacts(sec: Outline) = {

    val numbering = getNumbering(sec)

    Seq(
      KnowledgeArtifact(
        new URI(uri(sec)),
        new URI("_"),
        new URI(s"outline/title"),
        Option(sec.getTitle).getOrElse("").getBytes,
        Meta(new URI("freetext"))
      ),
      KnowledgeArtifact(
        new URI(uri(sec)),
        new URI("_"),
        new URI(s"outline/rhetorical"),
        Option(sec.getRhetorical).getOrElse("").getBytes,
        Meta(new URI("freetext"))
      ),
      KnowledgeArtifact(
        new URI(uri(sec)),
        new URI("_"),
        new URI(s"outline/level"),
        camelToUnderscores(sec.getClass.getSimpleName).getBytes,
        Meta(new URI("freetext"))
      ),
      KnowledgeArtifact(
        new URI(uri(sec)),
        new URI("_"),
        new URI(s"outline/numbering"),
        numbering.getBytes,
        Meta(new URI("freetext"))
      )
    )

  }

}

trait ExtractSections extends CasModel with ModelTransRules {

  def sections = JCasUtil.iterator(view, classOf[Section]).asScala.toList

  def subSections = JCasUtil.iterator(view, classOf[SubSection]).asScala.toList

  def genContentArtifacts(sec: Section) = {
    Seq(
      KnowledgeArtifact(
        new URI(uri(sec)),
        new URI("_"),
        new URI(s"section/title"),
        sec.getTitle.getBytes,
        Meta(new URI("freetext"))
      ),
      KnowledgeArtifact(
        new URI(uri(sec)),
        new URI("_"),
        new URI(s"section/rhetorical"),
        sec.getRhetorical.getBytes,
        Meta(new URI("freetext"))
      )
    )
  }

  def genContentArtifacts(sec: SubSection) = {
    val rethorical = if (sec.getRhetorical != null) {
      KnowledgeArtifact(
        new URI(uri(sec)),
        new URI("_"),
        new URI(s"section/rhetorical"),
        sec.getRhetorical.getBytes,
        Meta(new URI("freetext"))
      )
    } else {
      KnowledgeArtifact(
        new URI(uri(sec)),
        new URI("_"),
        new URI(s"section/rhetorical"),
        "".getBytes,
        Meta(new URI("freetext"))
      )
    }

    Seq(
      KnowledgeArtifact(
        new URI(uri(sec)),
        new URI("_"),
        new URI(s"section/title"),
        sec.getTitle.getBytes,
        Meta(new URI("freetext"))
      ), rethorical
    )
  }

}

trait ExtractSentences extends CasModel with ModelTransRules {

  def sentences = JCasUtil.iterator(view, classOf[Sentence]).asScala.toList

  def sentences(superordinate: Paragraph) =  JCasUtil.subiterate(view, classOf[Sentence], superordinate, true, false).iterator.asScala.map(sent => (sent, superordinate)).toList

  def sentences(superordinate: Abstract) =  JCasUtil.subiterate(view, classOf[Sentence], superordinate, true, false).iterator.asScala.map(sent => (sent, superordinate)).toList

  def sentences(superordinate: DocumentTitle) =  JCasUtil.subiterate(view, classOf[Sentence], superordinate, true, false).iterator.asScala.map(sent => (sent, superordinate)).toList

  def genTopologyArtifact(sent: Sentence, par: Paragraph, nofSentence: Int) = {
    KnowledgeArtifact(
      new URI(uri(sent)),
      new URI(sigmaticUri),
      new URI(s"topo/${uri(par)}"),
      s"${(1 + nofSentence) * 128}".getBytes,
      Meta(new URI("topo-rank@v1"))
    )
  }

  def genTopologyArtifact(sent: Sentence, abs: Abstract, nofSentence: Int) = {
    KnowledgeArtifact(
      new URI(uri(sent)),
      new URI(sigmaticUri),
      new URI(s"topo/${uri(abs)}"),
      s"${(1 + nofSentence) * 128}".getBytes,
      Meta(new URI("topo-rank@v1"))
    )
  }

  def genTopologyArtifact(sent: Sentence, dt: DocumentTitle, nofSentence: Int) = {
    KnowledgeArtifact(
      new URI(uri(sent)),
      new URI(sigmaticUri),
      new URI(s"topo/${uri(dt)}"),
      s"${(1 + nofSentence) * 128}".getBytes,
      Meta(new URI("topo-rank@v1"))
    )
  }

  def genContentArtifact(sent: Sentence) = {
    val text = sent.getCoveredText.getBytes
    KnowledgeArtifact(
      new URI(uri(sent)),
      new URI("_"),
      new URI("sentence/sentence"),
      text,
      Meta(new URI("freetext"), MurmurHash3.bytesHash(text))
    )
  }

}

trait ExtractNNEs extends CasModel with ModelTransRules {

  def nnes = JCasUtil.iterator(view, classOf[NormalizedNamedEntity]).asScala.toList

  def nnes(superordinate: Sentence) =  JCasUtil.subiterate(view, classOf[NormalizedNamedEntity], superordinate, true, false).iterator.asScala.map(nne => (nne, superordinate)).toList

  def genAnnotationArtifact(nne: NormalizedNamedEntity, sen: Sentence) = {
    val annotModel = s"""{
      "begin": ${nne.getBegin - sen.getBegin},
      "end": ${nne.getEnd - sen.getBegin},
      "attr": "sentence/sentence",
      "ref": "${uri(nne)}"
    }""".getBytes

    Seq(
      KnowledgeArtifact(  // In Annotation Layer "Concept Source"
        new URI(uri(sen)),
        new URI(dictId(nne)),
        new URI(uri(nne)),
        annotModel,
        Meta(new URI("annotation@v1"), MurmurHash3.bytesHash(annotModel))
      ),
      KnowledgeArtifact(  // In Annotation Layer "Collection Name"
        new URI(uri(sen)),
        new URI(layerUri),
        new URI(uri(nne)),
        annotModel,
        Meta(new URI("annotation@v1"), MurmurHash3.bytesHash(annotModel))
      )
    )
  }

}

trait ExtractFrontMatter extends CasModel with ModelTransRules {

  def frontMatters = JCasUtil.iterator(view, classOf[FrontMatter]).asScala.toList

  def documentTitles = JCasUtil.iterator(view, classOf[DocumentTitle]).asScala.toList

  def documentTitles(superordinate: FrontMatter) =  JCasUtil.subiterate(view, classOf[DocumentTitle], superordinate, true, false).iterator.asScala.map(dTitle => (dTitle, superordinate)).toList

  def documentAbstracts(superordinate: FrontMatter) =  JCasUtil.subiterate(view, classOf[Abstract], superordinate, true, false).iterator.asScala.map(dAbstract => (dAbstract, superordinate)).toList

  def genTopologyArtifact(frontMatter: FrontMatter) = {
    KnowledgeArtifact(
      new URI(uri(frontMatter)),
      new URI(sigmaticUri),
      new URI(s"topo/${sigmaticUri}"),
      "128".getBytes,
      Meta(new URI("topo-rank@v1"))
    )
  }

  def genTopologyArtifact(dTitle: DocumentTitle, frontMatter: FrontMatter) = {
    KnowledgeArtifact(
      new URI(uri(dTitle)),
      new URI(sigmaticUri),
      new URI(s"topo/${uri(frontMatter)}"),
      "128".getBytes,
      Meta(new URI("topo-rank@v1"))
    )
  }

  def genTopologyArtifact(dAbstract: Abstract, frontMatter: FrontMatter) = {
    KnowledgeArtifact(
      new URI(uri(dAbstract)),
      new URI(sigmaticUri),
      new URI(s"topo/${uri(frontMatter)}"),
      "256".getBytes,
      Meta(new URI("topo-rank@v1"))
    )
  }

  def genContentArtifact(dTitle: DocumentTitle) = {
    val text = dTitle.getCoveredText.getBytes
    KnowledgeArtifact(
      new URI(uri(dTitle)),
      new URI("_"),
      new URI("document-title/document-title"),
      text,
      Meta(new URI("freetext"), MurmurHash3.bytesHash(text))
    )
  }

}

trait ExtractHeader extends CasModel with ModelTransRules {

  def genContentArtifacts(header: Header) = {

    val authors = header.getAuthors.toArray
      .map(_.asInstanceOf[Person])
      .map(p => s"${p.getForename} ${p.getSurname}")
      .mkString(", ")

    val pubDate = String.format("%tF", header.getPublicationDate.getDate)

    Seq(
      KnowledgeArtifact(
        new URI(sigmaticUri),
        new URI("_"),
        new URI("header/title"),
        Option(header.getTitle).getOrElse("").getBytes,
        Meta(new URI("freetext"))
      ),
      KnowledgeArtifact(
        new URI(sigmaticUri),
        new URI("_"),
        new URI("header/authors"),
        authors.getBytes,
        Meta(new URI("freetext"))
      ),
      KnowledgeArtifact(
        new URI(sigmaticUri),
        new URI("_"),
        new URI("header/publicationDate"),
        pubDate.getBytes,
        Meta(new URI("freetext"))
      ),
      KnowledgeArtifact(
        new URI(sigmaticUri),
        new URI("_"),
        new URI("header/source"),
        headerSource.getBytes,
        Meta(new URI("URI"))
      ),
      KnowledgeArtifact(
        new URI(sigmaticUri),
        new URI("_"),
        new URI("header/id"),
        headerRawId.getBytes,
        Meta(new URI("freetext"))
      )
    )
  }

}
