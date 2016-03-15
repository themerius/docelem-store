// Main Authority des Systems ist d.f.s = de.fraunhofer.scai
// bzw. der Domainname ist scai.fraunhofer.de

// Es wurde für d.f.s ein Shortcut Mapping definiert
// d.f.s : pubmed -> gov.pubmed

// Benutzer tippt in den Browser http://s.f.de/pubmed:medlinecitation/121212

gov.pubmed/medlinecitation/121212     // Das hier ist quasi nur ein Meta-DocElem...
:  gov.pubmed/medlinecitation/121212  // 0-layer
:  d.f.s/version/fingerprint \0 d.f.s/annot/annot@v1 \0 a1
:  target=gov.pubmed/medlinecitation/121212@h1,pos=

// Der Viewer hat eine Regel definiert dass im 0-layer
// version/fingerprint behandelt wird:
// Es wird nach der neusten Version gefiltert und ein redirect drauf gemacht.

gov.pubmed/medlinecitation/121212@h1
:  gov.pubmed/medlinecitation/121212@h1  // 0-layer
:  gov.pubmed/medlinecitation/medlinecitation \0 gov.pubmed/medlinecitation/medlinecitation@v2016 \0 h1
:  <MedlineCitation>
     <PMID>121212</PMID>  // daraus wurde, in diesem Fall, die uid gewonnen
     <Abstract>...</Abstract>
    </MedlineCitation>

gov.pubmed/medlinecitation/121212@h1
:  d.f.s/run/mesh-v2-pubmed-12  // Wörterbuch-Version und Korpus
:  d.f.s/terminology/MeSH \0 d.f.s/annot/annot@v1 \0 a2
:  target=d.f.s.prominer/concept/HS0002,pos=(35,45)

gov.pubmed/medlinecitation/121212@h1
:  d.f.s/run/belief-15  // Run 15 von Belief, mehr dazu steht im DocElem (Startzeit, Version, Korpus etc.)
:  d.f.s/relation/BEL \0 d.f.s/annot/annot@v1 \0 a2
:  target=org.openbel/bel/f87a,pos=(35,45),(70,75),(90,93)

// Der Viewer holt sich alle Attribute und Annotationen ab und zeigt sie an.
// Ggf. kann der Viewer auf bestimmte Attribute/Annotationen mit Regeln reagieren.

org.openbel/bel/f87a
:  org.openbel/bel/f87a
:  org.openbel/bel/bel \0 org.openbel/bel/bel#v2 \0 f87a
:  p(HGNC:CCND1) => kin(p(HGNC:CDK4))

gov.pubmed/medlinecitation/medlinecitation
:  gov.pubmed/medlinecitation/medlinecitation  // 0-layer
:  d.f.s/version/namedTag \0 d.f.s/annot/annot@v1 \0 a3
:  target=gov.pubmed/medlinecitation/medlinecitation@v2016,pos=

gov.pubmed/medlinecitation/medlinecitation@v2016
:  gov.pubmed/medlinecitation/medlinecitation@v2016  // 0-layer
:  gov.pubmed/medlinecitation/medlinecitation \0 d.f.s/freetext/freetext@v1 \0 f1
:  Basiert auf der Spezifikation von https://www.nlm.nih.gov/bsd/licensee/elements_descriptions.html

Andere Beispiele:
PNG+Figure?
ScannedHeader wird zu richtigem Header+DocElems?


Möglichkeit 1, als Attribut:

d.f.s/figure/123
: d.f.s/figure/123
: d.f.s/figure/figure \0 org.libpng/png/png@v1.2 \0 7f19
: BINARY PNG

d.f.s/figure/123
: d.f.s/figure/123
: d.f.s/figure/caption \0 d.f.s/freetext/freetext@v1 \0 f2
: This figure shows...

// Text Mining on figure/caption
d.f.s/figure/123
: d.f.s/run/mesh-v2-figure-captions-2  // Das muss natürlich nicht alles in die UID gekodet werden..
: d.f.s/terminology/MeSH \0 d.f.s/annot/annot@v1 \0 a6
: target=...,pos=d.f.s/figure/caption(15,30)   // Annotation an ein Modell-Artefakt / Attribut

ODER
d.f.s/figure/123:d.f.s:figure:caption
: d.f.s/run/mesh-v2-figure-captions-2
: d.f.s/terminology/MeSH \0 d.f.s/annot/annot@v1 \0 a6
: target=...,pos=(15,30)

ODER
d.f.s/figure/caption@f2
: d.f.s/run/mesh-v2-figure-captions-2
: d.f.s/terminology/MeSH \0 d.f.s/annot/annot@v1 \0 a6
: target=d.f.s/figure/123/d.f.s:run:mesh-v2-figure-captions-2/d.f.s:figure:caption,pos=(15,30)

Möglichkeit 2, als Annotation:
