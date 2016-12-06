package eu.themerius.docelemstore.utils

import org.apache.accumulo.core.client.lexicoder.Lexicoder
import org.apache.accumulo.core.iterators.ValueFormatException

object Stats {
  def time[A](msg: String = "Default")(f: => A) = {
    val s = System.nanoTime
    val ret = f
    println(s"${msg} time: ${(System.nanoTime-s)/1e6} ms")
    ret
  }
}


class PMIDLexicoder extends Lexicoder[String] {

  // See Accumulo Book page 288-294

  override def encode(s: String): Array[Byte] = {
    s.split(":") match {
      case Array(prefix, pmid) if prefix.contains("PMID") => {
        (prefix + ":" + "%08d".format(pmid.toInt)).getBytes
      }
      case Array(prefix, pmc) if prefix.contains("PMCID") => {
        (prefix + ":PMC" + "%08d".format(pmc.replace("PMC", "").toInt)).getBytes
      }
      case _ => s.getBytes
    }
  }

  override def decode(b: Array[Byte]): String = {
    try {
      val s = new String(b)
      s.split(":") match {
        case Array(prefix, pmid) if prefix.contains("PMID") => {
          prefix + ":" + pmid.toInt
        }
        case Array(prefix, pmc) if prefix.contains("PMCID") => {
          prefix + ":PMC" + pmc.replace("PMC", "").toInt
        }
        case _ => s
      }
    } catch {
      case e: Exception => throw new ValueFormatException(e.getMessage)
    }
  }

}
