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

trait ModelTransRules extends Model {

  /*
   * Specific transformation of a given model into
   * the generic document element data structure.
   */
  def applyRules: Corpus = Corpus(Nil)  // TODO: should get the Model as parameter?

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
