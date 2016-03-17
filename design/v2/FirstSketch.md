Header
  :header/title: FreeText "The title"
  :header/author: Ref "Person/shodapp"
  :header/author: FreeText "Marc Zimmermann"
  :header/author: CardDAV "...N:Mustermann;Max\nORG:Self Employed..."

Section
  :section/title: FreeText "Introduction"

Paragraph
  :paragraph/paragraph: FreeText "This is my paragraphs text..."

Figure
  :figure/figure: PNG "...png binary..."
  :figure/caption: FreeText "The figure shows that..."

Section
  :section/title: FreeText "Method"

Paragraph
  :paragraph/paragraph: FreeText "A other paragraph"

Chemical
  :chemical/chemical: SMILES "CC(C)(C)O"
  :chemical/name: FreeText "tert-Butanol"


Tables to load on startup?
==========================

paragraph@v1/ ; "" ; descr ; Ein Paragrap ist ein aufgeschriebener Gedanke
paragraph@v1/ ; "" ; owlClass ; http://purl.org/spar/doco/Paragraph v1.3
paragraph@v1/ ; "" ; scaiImpl ; de.fraunhofer.scai.Paragraph v7.0
paragraph@v1/paragraph ; "" ; descr ; Main Attribut of paragraph.

Topology (jedes Attribut ein extra DocElem => macht Sinn):
* paragraph@v1
  * paragraph@v1/paragraph
  * paragraph@v1/rhetorical

<!-- mesh@v4/ ; "" ; descr ; "MeSH Dict enthält xy Nominklatur"
mesh@v4/nne ; "" ; descr ; "Normalized Named Entity mit Mesh IDs"
mesh@v4/nne ; "" ; owlClass ; http://textmining.org/NNE v1.1

ODER doch besser?: -->

nne@v1/ ; "" ; descr ; "Normalized Named Entity sind erkannte Konzepte..."
nne@v1/ ; "" ; owlClass ; http://textmining.org/NNE v1.1
nne@v1/mesh; "" ; descr ; "NNE welches die MeSH Vokabeln erhält"
nne@v1/mesh; "" ; url ; "https://www.nlm.nih.gov/mesh/"


// Alternativ zu run-nr kann man auch eine bestimmte BELIEF version nennen, würde auch Sinn machen. (Man kann ja beides machen)
runs/name:belief-15 ; name:belief-15, descr, "Run using Mesh, HGNC, over 25k Abstracts"

Wie am besten die NULL LAYER darstellen? Als sich selbst beschreibend oder einfach als leerer String? Was klar ist; das NULL LAYER muss gut automatisch zu idetifizieren sein. Sinn macht es durchaus einfach auf sich selbst zu zeigen... Wobei leer stehen lassen auch Sinn macht (aber nicht so generisch ist), weil das u.A. bestimmt Platz spart! (Bei Milliaren Attribut-Einträgen kommen vielleichts schon einige Gigabytes zusammen)

Wohin muss die Versionsnummer/Tag? Es muss einfach zu scannen sein! Und auch einfach zum Einschränken.


// v1 gewonnen aus allen paragraph-attributen im 0layer
paragraph/uuid:15ff@v1
  uuid:15ff // null layer
    paragraph@v1/paragraph FreeText "A other paragraph"   // MAIN ATTRIBUTE
    paragraph@v1/rhetorical FreeText "A other paragraph"   // MAIN ATTRIBUTE
  runs/name:belief-15   // layer für belief run
    nne@v1/mesh Ref "mesh/MDC002854@v4"
    nne@v1/mesh:MDC002854@v4 Annot <text:NormalizedNamedEntity xmi:id="8359" sofa="52" begin="1228" end="1235" wasGeneratedBy="258" concept="6973" synonym="17625"/>  // WAS MACHEN WIR MIT DEN XMI-IDs ETC.??
    nne@v1/mesh:MDC002854@v4 Annot <text:NormalizedNamedEntity begin="1228" end="1235" wasGeneratedBy="user/shodapp" concept="nne@v1/mesh:MDC002854@v4" synonym="synonym/name:taa"/>  // WAS MACHEN WIR MIT DEN XMI-IDs ETC.??

paragraph/uuid:15ff@v2
  NullLayer
  paragraph@v1/paragraph FreeText "A other paragraph"   // MAIN ATTRIBUTE

BEL Relationen
==============
