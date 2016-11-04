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

class SimpleWalModel extends Model with ModelTransRules {

  var reader: java.io.BufferedReader = null
  var buffer = Corpus(Nil)

  def deserialize(m: Array[Byte]) = this.synchronized {
    var line = reader.readLine
    var count = 0
    while (line != null) {
      count = count + 1
      println(count)
      buffer = Corpus(buffer.artifacts :+ parseLine(line))
      line = reader.readLine
    }
    this
  }

  def serialize: Array[Byte] = Array[Byte]()

  def parseLine(line: String) = {
    line.split("\t") match {
      case Array(time, sig, prag, sem, spec, model) => {
        KnowledgeArtifact(
          new URI(sig),
          new URI(prag),
          new URI(sem),
          model.replaceAll("\\\\n", "\n").getBytes,
          Meta(new URI(spec), MurmurHash3.stringHash(model), time.toLong)
        )
      }
      case _ => {
        println(s"of fuck $line")
        KnowledgeArtifact(
          new URI(""),
          new URI(""),
          new URI(""),
          "".getBytes,
          Meta(new URI(""))
        )
      }
    }
  }

  override def applyRules: Corpus = buffer

}
