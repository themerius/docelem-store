RELATIONS mit DocElems (Darstellung, efficient Scan)
=========

Die folgenden Annotationen sind an z.B. paragraph/ff65.

(1) Zur Anzeige eine Annotation mit meheren Positionen.
Quasi eine normale Annotaiton.

run/belief-15, bel/document, to=bel/15ff,pos=...  // quasi normale annotation?

(2) Oder direkt das Modell hier abspeichern, weil es eh nur für diese Evidenz gilt?

run/belief-15, bel/header, "import HGNC..."
run/belief-15, bel/statement, "complex(HGNC:...) -> p(MeSH:...)"

Zur Suche die SPO-Relationen einzeln. Das ist vergleichbar mit den Topologie-Annotationen nur noch genauer auf Relationsebene? Vielleicht kann man hier auch noch den Rank sinnvoll benutzen. (Schließlich bilden Programmiersprachen auch Bäume...)

run/belief-15/spo, bel/s(HGNC:), to=bel/15ff
run/belief-15/spo, bel/p(increases), to=bel/15ff

Bei Bedarf kann man auch direkt die BEL-Terms abspeichern und suchbar machen.
Dann kann man ganz tief einsteigen! (Das sollten wir erst dann implementieren wenn es gewünscht wird)

run/belief-15/spo, bel/func/complex(HGNC:...)


Darstellung von Inner-DocElem-Connections:

run/belief-15/spo, bel/s(HGNC:), to=paragraph/ff65?l=run/belief-15&a=bel/statement

Zugriff auf ein Attribut (return a list of statements):
http://.../paragraph/ff65?l=run/belief-15&a=bel/statement

Zugriff auf ein Attribut (return single model):
http://.../paragraph/ff65?l=run/belief-15&a=bel/statement&f=15ff

Wenn kein Fingerprint vorhanden ist handelt es sich um ein "skalares" Attribut und wenn Fingerprints vorhanden sind dann ist es ein "vektor" Attribut.


bel/15ff
  "_"
    bel/header "import HGNC..."
    bel/statement "complex(HGNC:...) -> p(MeSH:...)"


Fragen an Marc:
Soll ein BEL Statement mit allen Evidenzen geteilt werden?
Oder sollen wir lieber ein Statement pro Evidenz erstellen?
Weil wenn wir die Kuration überlegen sollte das Statement ja eher an die Evidenz gekoppelt sein.
Sollen wir ein gesamtes BEL-Dokument dort ablegen? Also mit Imports etc.? => Müssen wir ja fast, damit das BEL-Modell auch für sich selbst stehen kann / in sich geschlossen ist.
Man sollte auch beachten, dass man evneutell ein BEL-Dokument/Statement für sich selbst stehend im Dokument nennen/darstellen möchte.
