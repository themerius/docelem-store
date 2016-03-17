header/id:15
  "_"
    topo version "v5"??


section/s:1
  "_"
    hasVersion InternalReference "section/s:1@v3"
  "header/id:15"
    topo/f topoRank "rank=128,tag=v5,iref=section/s:1@v3"

section/s:2
  "_"
    hasVersion InternalReference "section/s:2@v1"
  "header/id:15"
    topo/f topoRank "rank=256,tag=v5,iref=section/s:2@v1"

paragraph/p:1
  "_"
    hasVersion InternalReference "paragraph/p:1@v1"
  "header/id:15"
    topo/n topoRank "rank=128,tag=v5,iref=paragraph/p:1@v1,f=section/s:1"
    topo/n topoRank "rank=128,tag=latest,iref=paragraph/p:1@v1,f=section/s:1"
  "header/id:16"
    topo/n@v5 topoRank "rank=128,iref=paragraph/p:1@v1,f=section/s:1"
    topo/n@latest topoRank "rank=128,iref=paragraph/p:1@v1,f=section/s:1"

paragraph/p:1@v1
  "header/id:16"
    topo/n@v5 topoRank "rank=128,iref=paragraph/p:1@v1,f=section/s:1"
    topo/n@latest topoRank (ohne fingerprint) "rank=128,iref=paragraph/p:1@v1,f=section/s:1"

Unabhänigkeit.
Die n's (nextChilds) kennen nur ihr f und haben einen Rang.
Die f's (firstChild) kennen ihr f bzw. das Hauptdokument und ihren Rang.

Der Rang ist nur in der lokalen Hierarchie gültig.
Zudem ist er ein vielfaches von 2, damit man bei einer Verschiebung nur das betreffende Element im Rang zwischen die umschließenden Elemente packen muss.

In den Annotations/Attribut-Index kommen dann
0, header/id:15!topo/n, paragraph/p:1
0, header/id:15!topo/n@latest, paragraph/p:1@v1

Dann kann man nach allen n's und f's eines Dokuments suchen!

Sollen die Tags dann Teil des Attributes sein? Beispiel:
topo/f@v5
topo/f@latest

Und sollen die Annotationen dann eher in das Meta-Element oder in das ganz konkrete Element?

Wenn eine Topologie getagged wird (also einen namen bekommt), muss eben zu jedem beteiligen Dokument eine entsprechende Annotation getätigt werden.
