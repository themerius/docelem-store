System
  - reads config, how much Gates and which Translators should be used
  - spawns a amount of Gates

Gate [few]
  - connections to broker
  - routing messages (raw xml strings) to Translator(s)

  - send Futures from Storage to broker (should be non-blocking, or we should construct a special OutGate?)

(Accumulo)Translator [many]
  - parsing xml (string -> scala xml nodes)
  - pre-calculate hashes or ids
  - create database specific structures (xml nodes -> accumulo mutations)
  - routing structures to Storage

  - translate query requests also into suitable structures if possible

(Accumulo)Storage [few]
  - keep connection to database (or start a single embedded (e.g. if db is threadsafe write async!))
  - feed strcutures right to the database
  - maybe optimize flushes,
    maybe collect single data and write in batches,
    maybe pre-dedupe before writing to db,
    reuse writers etc.

  - queries are non-blocking and answering as Future?

Possible Engines:
  - Accumulo
  - OrientDB
  - Lucene
  - H2 Database (Slick?)
  - Flatfile (CSV/XML)

Messages for Gate to handle (writing and answers):

    event: found document elements
    indexing?: add to scaiview index
    <docelems>
      <docelem>authority, type, uid, model, (hash = null, ts = null)</docelem>
    </docelems>

    event: found annotations
    indexing?: add to scaiview index
    <annotaitons>
      <annotation layer="topology:rootUuid" purpose="firstChild">
        <from fversion="ts/hash-nr">scai.fhg.de/Abstract/23664431</from>
        <to tversion="ts/hash-nr">scai.fhg.de/Person/person-001</to>
        <position>(11, 20)</position>
        ...other props
      </annotation>
    </annotaitons>

    event: found corpus
    indexing?: add to scaiview index
    <corpus>
      <docelems>
      <annotations>
    </corpus>

Messages for Gate to handle (reading / query):

    //event: looking for docelems (reply contents of docelems)
    //repy-to: /topic or /queue
    //<query>list of IDs (if needed: plus version-hash)</query>

    event: looking for docelem with annotations (replies <corpus> with annotations for a single docelem with via annotations connected docelems)
    reply-to: ...
    <query>single ID; filter for layer or purpose</query>

    event: looking for corpus/topology (replys all docelems with annotations within a topology)
    reply-to: ...
    <query>id of root docelem; filter for layer (this is the tag of the topology version) or purpose</query>

Define distinct In and Out Gate/Translator?

  - Storage should do both. (Because it can be a embedded database)
    Maybe it can be a singleton?
  - Specalized In/Out Translators are useful
    - Gate can distinct for two event categories: found and looking
    - These events categories needing always a other preparation
  - The Storage is able to delegate its answer in its own structure to a translator, which can form a unified message payload (e.g. xml string) out of the structure.
  - The Gate can wrap the message payload into a appropriate message and sends it.

  -> Only the Translators CAN be distincted.

  The Storage knows nothing about preperation of data for messages.
  The Translator knows nothing about how messages look and how the are sended.
  The Gate knows nothing about storages and preperation of messages. It only extracts/packages and routes.
