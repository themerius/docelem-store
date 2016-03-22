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

}

trait ModelTransRules extends Model {

  /*
   * Specific transformation of a given model into
   * the generic document element data structure.
   */
  def applyRules: Corpus = {
    Corpus(Nil)
  }

}
