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
   def applyRules: Corpus // TODO: return seq of doc elems

}
//
// trait DocElem2Record {
//
//   /*
//    * Transform document elements into a datastructure which is
//    * understood by the storage backend.
//    */
//
// }
//
// trait StorageBackend {
//
//   /*
//    * A storage backend where the document elements are persisted to.
//    */
//
// }
