// Speichern als Annotation
gov.pubmed/medlinecitation/121212@h1
:  d.f.s/topology/topology               // wir sammeln alle bekannten topologien im gleichen Layer
:  gov.pubmed/medlinecitation/121212@h1  // oder gehört es auch in das 0-layer?
:  d.f.s/topology/header \0 d.f.s/annot/annot#v1 \0 a1
:  target=d.f.s/topology/uuid@edition-1,pos=

d.f.s/topology/uuid@edition-1
:  d.f.s/topology/uuid@edition-1
:  d.f.s/topology/topology \0 org.w3/xml/scai.topo.dtd \0 edition-1    // fingerprint wurde einfach aus modell ausgelesen -> named tag! (bei draft wird noch gearbeitet...)
:  <head tag="edition-1" id="gov.pubmed/medlinecitation/121212@h1">
    <node id="d.f.s/paragraph/0f@edition-1"></node>
    <node id="d.f.s/figure/a0@h3"></node>
   </head>

Kann man statt einer random uuid einen schematischen uid wie gov.pubmed:medlinecitation:121212 wählen?
Mappings in URI?:
  / -> :
  @ -> !
  # -> ~
  . -> .
Die Hoffung ist immer gut lesbare URIs zu erhalten, die wieder leicht parsebar zu UIIDs sind.

// Das part_of DocElem
d.f.s/paragraph/0f@h2
:  gov.pubmed/medlinecitation/121212@h1     // im Kontext des Header DocElems
:  d.f.s/topology/part_of \0 d.f.s/annot/annot#v1 \0 a1
:  target=d.f.s/topology/uuid@edition-1,pos=

Im Browser:

http://scai.fraunhofer.de/paragraph/0f@h2?topo=gov.pubmed:medlinecitation:121212@h1&expand=d.f.s:topology:uuid@edition-1

Oder

http://scai.fraunhofer.de/paragraph/0f@h2?expand=d.f.s:topology:uuid!h1@edition-1  // wenn uuid schemantisch an layer angelehnt?
   // wenn nur eine topologie (part_of) existiert kann man auch nur ?expand schreiben?

Das öffnet den kompletten Unterbaum für Artikel 121212 ab paragraph f0 in der
Topologieversion uuid@edition-1


----
also_known_as -> DocElem-Topologie-Technik.md
