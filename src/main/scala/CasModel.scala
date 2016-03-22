package eu.themerius.docelemstore

import java.io.ByteArrayInputStream
import java.net.URI

import de.fraunhofer.scai.bio.uima.core.util.UIMATypeSystemUtils
import de.fraunhofer.scai.bio.uima.core.util.UIMAViewUtils
import de.fraunhofer.scai.bio.uima.core.deploy.AbstractDeployer
import de.fraunhofer.scai.bio.uima.core.provenance.ProvenanceUtils
import org.apache.uima.fit.util.JCasUtil
import de.fraunhofer.scai.bio.msa.util.MessageUtils
import de.fraunhofer.scai.bio.extraction.types.text.NormalizedNamedEntity;
import org.apache.uima.cas.CAS

import scala.util.hashing.MurmurHash3



trait CasModel extends Model {
  var cas: CAS = _
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

trait ExtractNNEs extends CasModel with ModelTransRules  {

  override def applyRules = {
    val jcas = cas.getJCas()
    val view = UIMAViewUtils.getOrCreatePreferredView(jcas, AbstractDeployer.VIEW_DOCUMENT)
    val header = UIMAViewUtils.getHeaderFromView(jcas)

    val annotationLayer = ProvenanceUtils.getDocumentCollectionName(jcas)
    val layerUri = s"run/name:${annotationLayer}"

    val userId = ProvenanceUtils.getUserSuppliedID(jcas)
    val pmidId = userId.split("PMID")

    val sigmaticType = header.getDocumentConcept
    val sigmaticUri = (sigmaticType, pmidId) match {
      case (null, Array("", id)) => s"header/pmid:${id}"
      case _ => s"${sigmaticType}/name:${userId}"
      // TODO: we need the sigmatic id type in the uima type system!?
    }

    // TODO: Sub-Iterator for Title or Abstract or Sentences?
    val it = JCasUtil.iterator(view, classOf[NormalizedNamedEntity])

    var artifacts = Seq[KnowledgeArtifact]()

    while (it.hasNext) {
      val nne = it.next

      val dictId = nne.getConcept.getIdentifierSource.replace(".syn", "").toLowerCase
      val conceptId = nne.getConcept.getIdentifier
      val prefName = nne.getConcept.getPrefLabel.getValue.replaceAll("\\s", "_").toLowerCase

      val attrUri = s"concept/${dictId}:${prefName}"
      val annotModel = s"{pos:[${nne.getBegin},${nne.getEnd}]}".getBytes("UTF-8")
      // TODO: into annotModel should be a link to a attribut of the 0layer.

      // Prepend all artifacts (this is O(1) on immutable lists)
      artifacts = KnowledgeArtifact(
        new URI(sigmaticUri),
        new URI(layerUri),
        new URI(attrUri),
        annotModel,
        Meta(new URI("annotation@v1"), MurmurHash3.bytesHash(annotModel))
      ) +: artifacts
    }

    Corpus(artifacts ++ super.applyRules.artifacts)
  }

}

trait ExtractSCAIViewAbstracts extends CasModel with ModelTransRules  {

  override def applyRules = {
    val jcas = cas.getJCas()
    val view = UIMAViewUtils.getOrCreatePreferredView(jcas, AbstractDeployer.VIEW_DOCUMENT)
    val header = UIMAViewUtils.getHeaderFromView(jcas)

    //val annotationLayer = ProvenanceUtils.getDocumentCollectionName(jcas)
    val layerUri = "_"

    val userId = ProvenanceUtils.getUserSuppliedID(jcas)
    val pmidId = userId.split("PMID")

    val sigmaticType = header.getDocumentConcept
    val sigmaticUri = (sigmaticType, pmidId) match {
      case (null, Array("", id)) => s"header/pmid:${id}"
      case _ => s"${sigmaticType}/name:${userId}"
      // TODO: we need the sigmatic id type in the uima type system!?
    }

    var artifacts = Seq[KnowledgeArtifact]()

    artifacts = KnowledgeArtifact(
      new URI(sigmaticUri),
      new URI(layerUri),
      new URI("header/header"),
      view.getDocumentText.getBytes,
      Meta(new URI("scaiview.abstract"))
    ) +: artifacts

    artifacts = KnowledgeArtifact(
      new URI(sigmaticUri),
      new URI(layerUri),
      new URI("header/authors"),
      header.getAuthors.toStringArray.mkString(", ").getBytes,
      Meta(new URI("freetext"))
    ) +: artifacts

    artifacts = KnowledgeArtifact(
      new URI(sigmaticUri),
      new URI(layerUri),
      new URI("header/publicationDate"),
      header.getPublicationDate.getDate.toString.getBytes,
      Meta(new URI("freetext"))
    ) +: artifacts

    Corpus(artifacts ++ super.applyRules.artifacts)
  }

}
