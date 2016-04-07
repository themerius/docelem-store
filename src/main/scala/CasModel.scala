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
import de.fraunhofer.scai.bio.extraction.types.text.documentelement.structure.Paragraph
import de.fraunhofer.scai.bio.extraction.types.meta.Person
import org.apache.uima.cas.CAS

import scala.util.hashing.MurmurHash3



trait CasModel extends Model {

  var cas: CAS = _

  def jcas = cas.getJCas()
  def header = UIMAViewUtils.getHeaderFromView(jcas)

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

trait ExtractNNEs extends CasModel with ModelTransRules  {

  override def applyRules = {
    val view = UIMAViewUtils.getOrCreatePreferredView(jcas, AbstractDeployer.VIEW_DOCUMENT)
    val header = UIMAViewUtils.getHeaderFromView(jcas)

    // TODO: Sub-Iterator for Title or Abstract/GenericDocElem or Sentences?
    val it = JCasUtil.iterator(view, classOf[NormalizedNamedEntity])

    var artifacts = Seq[KnowledgeArtifact]()

    while (it.hasNext) {
      val nne = it.next

      val dictId = nne.getConcept.getIdentifierSource.replace(".syn", "").toLowerCase
      val conceptId = nne.getConcept.getIdentifier
      val prefName = nne.getConcept.getPrefLabel.getValue.replaceAll("\\s", "_").toLowerCase

      val attrUri = s"concept/${dictId}:${prefName}"
      val annotModel = s"""{"begin": ${nne.getBegin}, "end": ${nne.getEnd}, "attr": "header/header", "ref": "${attrUri}"}""".getBytes("UTF-8")

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

trait ExtractSentences extends CasModel with ModelTransRules  {

  override def applyRules = {
    val jcas = cas.getJCas()
    val view = UIMAViewUtils.getOrCreatePreferredView(jcas, AbstractDeployer.VIEW_DOCUMENT)
    val header = UIMAViewUtils.getHeaderFromView(jcas)

    val layerUri = "sentences"

    val it = JCasUtil.iterator(view, classOf[Sentence])

    var artifacts = Seq[KnowledgeArtifact]()
    var idx = 0

    while (it.hasNext) {
      val sent = it.next
      idx = idx + 1

      val attrUri = s"sentence/${idx}"
      val jsonPosition = s"""{"begin": ${sent.getBegin}, "end": ${sent.getEnd}, "attr": "header/header"}""".getBytes("UTF-8")
      // TODO: into jsonPosition should be a link to a attribut of the 0layer, e.g the sigmaticType.

      // Prepend all artifacts (this is O(1) on immutable lists)
      artifacts = KnowledgeArtifact(
        new URI(sigmaticUri),
        new URI(layerUri),
        new URI(attrUri),
        jsonPosition,
        Meta(new URI("annotation@v1"), MurmurHash3.bytesHash(jsonPosition))
      ) +: artifacts
    }

    Corpus(artifacts ++ super.applyRules.artifacts)
  }

}

// TODO: can we split that into multiple traits?
trait ExtractParagraphsAndSentencesAndNne extends CasModel with ModelTransRules {

  override def applyRules = {
    val jcas = cas.getJCas()
    val view = UIMAViewUtils.getOrCreatePreferredView(jcas, AbstractDeployer.VIEW_DOCUMENT)

    // This is because the paragraph is
    val documentHeaderUri = sigmaticUri

    var artifacts = Seq[KnowledgeArtifact]()
    var nofParagraph = 0

    val it = JCasUtil.iterator(view, classOf[Paragraph])

    while (it.hasNext) {
      val paragraph = it.next
      nofParagraph = nofParagraph + 1

      val hashString = s"${documentHeaderUri}:paragraph:${nofParagraph}"
      val hash = MurmurHash3.stringHash(hashString)
      val hashHex = Integer.toHexString(hash)

      val paragraphUri = s"paragraph/murmur3:${hashHex}"

      // Write topology information/annotation for paragraph
      artifacts = KnowledgeArtifact(
        new URI(paragraphUri),
        new URI(documentHeaderUri),
        new URI(s"topo/${documentHeaderUri}"),
        s"${nofParagraph * 128}".getBytes,
        Meta(new URI("topo-rank@v1"))
      ) +: artifacts

      // This paragraph is only a container for the senctences
      val sit = JCasUtil.subiterate(view, classOf[Sentence], paragraph, true, false).iterator
      var nofSentence = 0

      while (sit.hasNext) {
        val sentence = sit.next
        nofSentence = nofSentence + 1

        val text = sentence.getCoveredText
        val hash = MurmurHash3.stringHash(text)
        val hashHex = Integer.toHexString(hash)
        val sentenceUri = s"sentence/murmur3:${hashHex}"

        // Create the actual sentence
        artifacts = KnowledgeArtifact(
          new URI(sentenceUri),
          new URI("_"),
          new URI("sentence/sentence"),
          text.getBytes,
          Meta(new URI("freetext"), hash)
        ) +: artifacts

        // Create topology information
        artifacts = KnowledgeArtifact(
          new URI(sentenceUri),
          new URI(documentHeaderUri),
          new URI(s"topo/${paragraphUri}"),
          s"${nofSentence * 128}".getBytes,
          Meta(new URI("topo-rank@v1"))
        ) +: artifacts

        val nit = JCasUtil.subiterate(view, classOf[NormalizedNamedEntity], sentence, true, false).iterator

        while (nit.hasNext) {
          val nne = nit.next

          val dictId = nne.getConcept.getIdentifierSource.replace(".syn", "").toLowerCase
          val conceptId = nne.getConcept.getIdentifier
          val prefName = nne.getConcept.getPrefLabel.getValue.replaceAll("\\s", "_").toLowerCase

          val attrUri = s"concept/${dictId}:${prefName}"
          val annotModel = s"""{"begin": ${nne.getBegin - sentence.getBegin}, "end": ${nne.getEnd - sentence.getBegin}, "attr": "header/header", "ref": "${attrUri}"}""".getBytes("UTF-8")

          // Add the (concept) annotations to the sentence
          artifacts = KnowledgeArtifact(
            new URI(sentenceUri),
            new URI(layerUri),
            new URI(attrUri),
            annotModel,
            Meta(new URI("annotation@v1"), MurmurHash3.bytesHash(annotModel))
          ) +: artifacts
        }
      }
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

trait NneQueryBuilder extends CasModel with QueryBuilder {

  def buildQuery = {
    val jcas = cas.getJCas()
    val view = UIMAViewUtils.getOrCreatePreferredView(jcas, AbstractDeployer.VIEW_DOCUMENT)
    val header = UIMAViewUtils.getHeaderFromView(jcas)
    val userId = ProvenanceUtils.getUserSuppliedID(jcas)

    // TODO: generic layer... we need a better filled typesystem...
    val layer = s"header/name:$userId"

    val it = JCasUtil.iterator(view, classOf[NormalizedNamedEntity])

    var query = Seq.empty[scala.xml.Node]

    while (it.hasNext) {
      val nne = it.next

      val dictId = nne.getConcept.getIdentifierSource.replace(".syn", "").toLowerCase
      val conceptId = nne.getConcept.getIdentifier
      val prefName = nne.getConcept.getPrefLabel.getValue.replaceAll("\\s", "_").toLowerCase

      query :+= <concept>{s"${layer}!concept/${dictId}:${prefName}"}</concept>
    }
    // TODO: check if query is really valid
    // TODO: specify <undefined cause="..." /> for all projects! Also suitable for log entries? Or something like a error-docelem/corpus?
    if (query.nonEmpty)
      Query(QueryTarget.SemanticSearch, <query>{query}</query>)
    else
      Query(QueryTarget.Invalid, <query><undefined /></query>)
  }

}
