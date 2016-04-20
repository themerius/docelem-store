package eu.themerius.docelemstore

import java.io.ByteArrayInputStream
import java.net.URI

import de.fraunhofer.scai.bio.uima.core.util.UIMATypeSystemUtils
import de.fraunhofer.scai.bio.uima.core.util.UIMAViewUtils
import de.fraunhofer.scai.bio.uima.core.deploy.AbstractDeployer
import de.fraunhofer.scai.bio.uima.core.provenance.ProvenanceUtils
import org.apache.uima.fit.util.JCasUtil
import de.fraunhofer.scai.bio.msa.util.MessageUtils
import de.fraunhofer.scai.bio.extraction.types.text.NormalizedNamedEntity
import de.fraunhofer.scai.bio.extraction.types.text.Sentence
import de.fraunhofer.scai.bio.extraction.types.text.NLPRelation
import de.fraunhofer.scai.bio.extraction.types.text.documentelement.structure.Paragraph
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
    val userId = ProvenanceUtils.getUserSuppliedID(jcas)
    val pmidId = userId.split("PMID")
    var idType = "pmid"

    if (header.getDocumentConcept != null) {
      idType = header.getDocumentConcept.getIdentifier
    }

    pmidId match {
      case Array("", id) => s"header/${idType}:${id}"
      case _ => s"header/${idType}:${userId}"
    }
  }

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
  def uri(par: Paragraph) = {
    val hash = MurmurHash3.stringHash(par.getCoveredText)
    val hashHex = Integer.toHexString(hash)
    s"paragraph/murmur3:${hashHex}"
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

trait ExtractSentences extends CasModel with ModelTransRules {

  def sentences = JCasUtil.iterator(view, classOf[Sentence]).asScala.toList

  def sentences(superordinate: Paragraph) =  JCasUtil.subiterate(view, classOf[Sentence], superordinate, true, false).iterator.asScala.map(sent => (sent, superordinate)).toList

  def genTopologyArtifact(sent: Sentence, par: Paragraph, nofSentence: Int) = {
    KnowledgeArtifact(
      new URI(uri(sent)),
      new URI(sigmaticUri),
      new URI(s"topo/${uri(par)}"),
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
      "attr": "header/header",
      "ref": "${uri(nne)}"
    }""".getBytes

    KnowledgeArtifact(
      new URI(uri(sen)),
      new URI(layerUri),
      new URI(uri(nne)),
      annotModel,
      Meta(new URI("annotation@v1"), MurmurHash3.bytesHash(annotModel))
    )
  }

}

trait ExtractSCAIViewAbstracts extends CasModel with ModelTransRules  {

  override def applyRules = {
    val jcas = cas.getJCas()
    val view = UIMAViewUtils.getOrCreatePreferredView(jcas, AbstractDeployer.VIEW_DOCUMENT)
    val header = UIMAViewUtils.getHeaderFromView(jcas)

    //val annotationLayer = ProvenanceUtils.getDocumentCollectionName(jcas)
    val layerUri = "_"

    var artifacts = Seq[KnowledgeArtifact]()

    artifacts = KnowledgeArtifact(
      new URI(sigmaticUri),
      new URI(layerUri),
      new URI("header/header"),
      view.getDocumentText.getBytes,
      Meta(new URI("scaiview.abstract"))
    ) +: artifacts

    val authors = header.getAuthors.toArray.map(_.asInstanceOf[Person]).map(p => s"${p.getForename} ${p.getSurname}").mkString(", ")

    artifacts = KnowledgeArtifact(
      new URI(sigmaticUri),
      new URI(layerUri),
      new URI("header/authors"),
      authors.getBytes,
      Meta(new URI("freetext"))
    ) +: artifacts

    artifacts = KnowledgeArtifact(
      new URI(sigmaticUri),
      new URI(layerUri),
      new URI("header/publicationDate"),
      String.format("%tFT%<tRZ", header.getPublicationDate.getDate).getBytes,
      Meta(new URI("freetext"))
    ) +: artifacts

    Corpus(artifacts ++ super.applyRules.artifacts)
  }

}
