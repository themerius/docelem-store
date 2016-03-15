
Im Layer kann folgendes festgehalten werden:
  - Versionszugehörigkeit, Named Tags, Edition eines Dokuments, Fingerprint eines Modells
  - Wer das hingeschrieben hat (z.B. Person die einen Kommentar gegeben hat)
  - In welchem Kontext die Annotation steht
  - Welche Wörterbuch-Version verwendet wurde
  - Welcher Run verwendet wurde (Im Run-DocElem kann z.B. verwendete Software/Wörterbuchversion etc. aufgeführt sein)

Annotations Struct:
  - from
  - to / target
  - value (für einfache Werte, muss aber im Purpose-Geber (also ein Attribut-Spec) definiert sein)
  - position (innerhalb des modells z.B. begin/end bei freitexten)
  - layer
  - purpose
  - prov / blame (uiid zu Person/Programm die das annotiert hat)

h1=hash(BYTES1)
a1=hash(ANNOT1)

$a1 braucht es nur aus technischen Gründen.
Damit Accumulo mehrere Annotationen mit dem gleichen Purpose speichern kann.

In sys sind für das System definierte Aktionen (z.B. redirect) oder Attribute.
Authorities können ihre eigenen sys-Attribute schreiben und inividuell darauf reagieren.
(z.B. falls BELIEF eigene Aktionen hinzufügen will?)

Purpose zeigt auf die Spezifikation der Figure selbst.
Layer ist die Version; Das System weiß wie dieses Layer zu behandeln ist, z.B. dass die RowId für die Version entsteht.
Ohne # gelten die Annotationen/Models für alle Versionen.

d.f.s/figure/uid ; d.f.s/figure/uid#h1 ; d.f.s/figure/figure#edition1 ; BYTES1
d.f.s/figure/uid ; d.f.s/figure/uid#h1 ; d.f.s/sys/modelspec$a1 ; ANNOT1(to=header/freetext)

Purpose dient als Attribut:
d.f.s/figure/uid ; d.f.s/figure/uid#h1 ; d.f.s/figure/image$a2 ; ANNOT2(to=png/h2)
d.f.s/figure/uid ; d.f.s/figure/uid#h1 ; d.f.s/figure/caption$a3 ; ANNOT3(to=paragraph/h3)

d.f.s/figure/uid ; d.f.s/figure/uid#mytag ; d.f.s/sys/namedTag$a3 ; ANNOT3(to=figure/uid#h1)

                 (anweisung für z.b. viewer)  (caused by / because of)
d.f.s/figure/uid#mytag ; d.f.s/sys/redirect ; d.f.s/sys/namedTag$a3 ; ANNOT3(to=figure/uid#h1)
ODER
d.f.s/figure/uid#mytag ; d.f.s/sys/action ; d.f.s/sys/redirect$a3 ; ANNOT3(to=figure/uid#h1)
ODER
d.f.s/figure/uid#mytag ; d.f.s/user/sh ; d.f.s/sys/redirect$a3 ; ANNOT3(to=figure/uid#h1)

org.libpng/png/h2 ; org.libpng/png/h2 ; org.libpng/png/png#v1.3 ; BYTES2

If hash(BYTES) != uid then append_to_uid(#+hash(BYTES)) else uid_is_already_hash


d.f.s/sys/redirect  -> d.f.s/sys/redirect#latest
d.f.s/sys/redirect  => Wird abgebildet so im Programm? (Namespace, Klasse, Funktion(ANNOT-STRUCT))

Entsprechender Programmcode könnte sogar an die jeweilige Spezifikation annotiert werden!!

d.f.s/figure/figure#edition1 ; BYTES1
=>
d.f.s/figure/figure#edition1 ; BYTES1  => Definition des Konstruktors.
Version 'edition1' der Klasse muss gewählt werden ODER
der Konstruktur bekommt Figure(version='edition1', serialized_model=BYTES1)...
Oder kann man die Modelspec Annotation quasi als Magic Number/Line vor die BYTES hängen?
Dann ist man flexibler was das Modell betrifft! Dann gibt ANNOT(to=model/spec) den passenden Konstruktur...
