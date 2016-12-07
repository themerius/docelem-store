package eu.themerius.docelemstore

trait Model {

  /*
   * Transform a serialized model (binary, DSL, natural language)
   * into a model runtime instance.
   */
  def deserialize(m: Array[Byte]): Model

  /*
   * Serialize a models runtime instance into a byte array.
   */
  def serialize: Array[Byte]

  // TODO: generic method to get the specialized model instance...

}

case class RawData(dtype: String, data: Array[Byte])

trait ModelTransRules extends Model {

  /*
   * Specific transformation of a given model into
   * the generic document element data structure.
   */
  def applyRules: Corpus = Corpus(Nil)  // TODO: should get the Model as parameter?

  def getDocumentId: Option[String] = None
  def getDocumentLabel: Option[String] = None
  def rawTextMiningData: Option[RawData] = None
  def rawPlaintextData: Option[RawData] = None
  def rawOriginalData: Option[RawData] = None

}

trait QueryBuilder extends Model {

  /*
   * Try to interpret and build a query out of the given model.
   */
  def buildQuery: Query

}

// TODO: it makes sense to render here templates? Should all services communicate with (api) end users with a special semantic html (e.g. RDFa) format?
trait ResponseTemplate {

  def renderTemplate(corpus: Corpus): String

}
