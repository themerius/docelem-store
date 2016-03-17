In der URL, sollen dort ID-Typen verwendet werden oder DocElem-Typen?

Aus der Sicht eines Viewers macht es mehr Sinn dort DocElem-Typen anzusiedeln. Dann weiß der Viewer direkt welches Viewing-Modell er nehmen soll...

Vielleicht sollte man ggf. den ID-Typ als Teil der ID einpflegen?

http://scai.de/§/medlinecitation/PMID121212
http://scai.de/§/medlinecitation/PMID-121212

Oder es ist implizit klar, dass medlinecitation auf PMIDs setzt...


Aber man könnte sich auch einen allgemeineren Typ vorstellen wie header statt medlinecitaiton. header ist z.B. von SCAI und medlinecitation von pubmed.gov.

http://scai.de/§/header/PMID121212
http://scai.de/§/header/gov.pubmed.pmid.121212  // oder eben als teil der UID, da der Header von uns ausgegeben wird...
http://scai.de/§/header/pmid.121212

Oder man kombiniert es, header von uns pmid wird von pubmed.gov definiert...

http://scai.de/§/header:pmid/121212

Oder beides von pubmed

http://scai.de/§/medlinecitation:pmid/121212


Und medlinecitation und pmid sind mappings die in der Authority/Domain de.scai so gelten. (Bei Export werden dann wieder die Langnamen verwendet... Also falls eine andere Authority unsere DocElems importieren will...)

http://scai.de/§:medlinecitation:pmid/121212

http://scai.de/§:png:sha-256/ef8asd20dasdlf
http://scai.de/§:png/sha-256:ef8asd20dasdlf
http://scai.de/§:paragraph/uuid:ef8asd20dasdlf
http://scai.de/§:medlinecitation/pmid:121212

Das vor dem Doppelpunkt kann jeweils eine Abkürzung für eine Ontologie-URI sein?
Mit http://scai.de/§:medlinecitation kann man z.B. auf diesen Inhalt zugreifen? -> Also die Defintion bzw. Spezifikation

Version muss noch rein? Sollte ich Versionsinformation auch in die URL wandern?

Diese Links sollen quasi Permalinks sein. Daher sollten sie Immutable sein.
Oder will man eine bestimmte "Tag-Konfiguration" speziell markieren?
Also welche Version von jeweils medlinecitation, pmid und der UID (z.b 121212) war bei Access in Nutzung. => Versions-Vektor?

http://scai.de/§:medlinecitation/pmid:121212[v2016,v2016,v3]

http://scai.de/§:owl/name:doublin-core[v3] -> Zeigt auf die XML als Modell?
http://scai.de/§:owl/name:medlinecitation[v2016] -> Speichert die OWL-XML als Modell?

owl/name:medlinecitation, owl/name:medlinecitation, owl/owl | xml, <...>
