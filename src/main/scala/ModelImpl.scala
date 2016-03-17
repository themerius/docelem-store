package eu.themerius.docelemstore

import java.io.ByteArrayInputStream
import de.fraunhofer.scai.bio.uima.core.util.UIMATypeSystemUtils
import de.fraunhofer.scai.bio.msa.util.MessageUtils
import org.apache.uima.cas.CAS


trait CasModel extends Model {
  var cas: CAS = _
}

// Or better make static methods??

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

  def applyRules = {
    //super.applyRules
    println(cas)
  }

}
