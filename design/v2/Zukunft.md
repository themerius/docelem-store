Um den DocElem-Store herum können dann verschiedene Indices gebaut werden. Auch auf anderen Technologien wie Lucene oder Neo4j etc.

NAHE ZUKUNFT
============

Und alle Vokablen (und deren Versionen) wie z.B. paragraph etc. werden dann durch entsprechende OWL Onotolgien defineirt?

ZUKUNFT
=======

Man müsste auch noch Software schreiben, die es eröglicht, dass sich der DocElem Store ständig selbst wieder reorganisiert. Also wenn es z.B. mehrere "sameAs" Relationen zum gleichen Element gibt, dass sichergestellt wird, dass alle Infos unter einem einzigen gespeichert sind.


Neben dem Annotaiton-Index, auch noch einen SPO-Index aufbauen?
DocElem Table ist jedenfalls die Haupttabelle mit dem Content und Annotationen, ggf. muss man für Topologie-Infos wie Versionen und Corpera noch extra Indices anlegen?
