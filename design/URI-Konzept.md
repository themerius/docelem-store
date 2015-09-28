# Authorities and Namespaces

themerius.eu/deadbeef

themerius.eu/deadbeef?expand_corpus&annotation_layer=all#jump_to_docelem_from_topology

durch topologie hangeln (alternaive)

themerius.eu/ausgangspunkt_uuid/

pmid namespace for e.g. PubMedIdentifier namespace
(Namespace wurde von der themeierus.eu Orgainisation definiert...
die Langform wäre pmid.themerius.eu? Oder wäre pmid einfach die ID
eines Dokumentelements, welches die Erklärung liefert was pmid etc. zu bedeuten hat?)

themerius.eu/pmid:121212

themerius.eu/pmid/121212

als konvention für uuids? oder als extra property?
authority als extra property? (falls man fremde docelems importiert)

named docelems (bekannt unter anderen namen, eingeornet in einem namespace,
der namespace darf sich aber ggf. aendern -> redirect)

themerius.eu/chemistry/benzol  -wird_aufgeloest_zu-> themerius.eu/ef8i
themerius.eu/chemistry/benzol?mid -responses_the_meta_id-> <uuid>ef8i</uuid>
Meta ID Konzept?: Vereint URI (bzw. Namespaces) und UUID

## Meta ID Konzept / Unique Immutable Identifier

  - Vereint URI und UID/UUID
  - Immutalbe: ID ist konstant und verweist immer auf das gleiche Ding
  - Bei Verweisen wird immer die Immutable ID genommen!
  - Man könnte sowas wie Named DocElems erlauben, welche besser lesbare benutzerdef.
    IDs darstellen. Diese können sich dann auch verändern (redirect oder so),
    aber wird irgendwo ein Verweis gesetzt muss dieser die Immutable ID verwenden.
    Somit sind die Namend DocElems IDs nur ein Mapping auf eine echte ID.
    Könnte man als Annotaiton realisieren.

## Show things imported from other authority/namespace

themerius.eu/scai.fraunhofer.de:ef8i

scai.fraunhofer.de/ef8i

## Fremnde Ressourcen Annotieren (twitter/reddit like)

Man erstellt ein Meta-Dokelem von dem Eintrag
(mit Infos zum Autor, Webseiten-URL etc. oder z.B. Metadaten einer Buch-Zitation).
Und an dieses werden die zusätzlichen Infos annotiert (ohne den eigentlichen Inhalt zu übertragen).
Falls sich z.B. die URL der Originalressource geändert hat,
können diese mit einer "also_known_as/same_as..."-Relation zusammengeführt werden.

Wenn man z.B. einen anderen docelem-store hat (bietet z.B. API),
kann man auch den kompletten Inhalt in den eigenen Store importieren.
Dann braucht man jedoch die id des anderen docelem-stores (eben z.B. die URI/Authority).
(Das ist wichtig, damit wir die gleichen UUIDs auch in unseren DocStore
zur Verfügung haben, falls es Referenzierungen untereinander gibt!)

Klar kann man auch selbst einen Importer schreiben, der von einer nicht-doclelem
Ressource mehr als nur die Meta-Elemente besorgen kann...


# Neuer Versuch

themerius.eu/pubmed/121212
themerius.eu/paragraph/e29b-11d4
themerius.eu/corpus/a716-4466  -> corpus sind meta dokumentelemente, welche infos zum dokument enthalten (und der init/wurzel punkt für topologien/korpera sind)
themerius.eu/corpus/a716-4466?expand_corpus
themerius.eu/meta/paragraph  -> returns general meta infos about paragraphs

Kompletter Inhalt von Fremd-Store importiert:

themerius.eu/prominer/HS00001?from=scai.fhg.de
Implizit hat der themerius.eu server immer ?from=themerius.eu als Query.

Reines Meta Dokumentelement welches man z.B. von einer Webseite hat (bsp. Twitter):

themerius.eu/tweet/11d4-a716
Enthält jetzt nur z.B. die URL, Autor, Datum etc. und die von mir daran gehefteten Annotationen (z.B. eine Diskussion)
