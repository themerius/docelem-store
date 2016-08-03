package eu.themerius.docelemstore

import java.net.URI
import scala.util.hashing.MurmurHash3

class SimpleWalLineModel extends Model with ModelTransRules {

  var line = ""

  def deserialize(m: Array[Byte]) = {
    line = new String(m, "UTF-8")
    this
  }

  def serialize: Array[Byte] = line.getBytes

  override def applyRules: Corpus = {
    line.split("\t") match {
      case Array(time, sig, prag, sem, spec, model) => Corpus(Seq(
        KnowledgeArtifact(
          new URI(sig),
          new URI(prag),
          new URI(sem),
          model.replaceAll("\\\\n", "\n").getBytes,
          Meta(new URI(spec), MurmurHash3.stringHash(model), time.toLong)
      )))
      case _ => Corpus(Nil)
    }
  }

}
