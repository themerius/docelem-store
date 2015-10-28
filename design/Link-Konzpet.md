# Links im DocElem-Konzept

1. Externer Link
   (normaler Link ins Internet/URI/URL)
2. Internal Link
   (Wiki-ähnlicher Link, innerhalb der DocElems die im  Systems verfügbar sind)
3. Deep Link (Query-Lang ist JavaScript? (Properties sind JSON?))
   Beziehen sich auch auf Dinge innerhalb des Systems.
   Verweisen jedoch auf Content von anderen Docelems
   (Also ein Query an das andere DocElem).
   Querysprache wäre sicher z.B. JavaScript+JSON sehr geeignet.
   Bsp: <a href="figure/123789?auth=scai.fhg.de">Query</a>
   ?Inhalt dann auf Serverseite ersetzen mit dem eigentlichen Content,
   oder auf Clientseite durch JS ersetzen lassen?

Diese Links sind im Content/Modell hart verdrahtet;
eine "normale" Annotation ist das also nicht unbedingt.

Darstellung als klassisches Markup:

  ... auf Abbildung <query>someFig.nr</query> ist zu sehen ...

Im Prinzip kann man sich das auch als "Magic Character" (z.B. \0) vorstellen,
der eine zu füllende Lücke darstellt.
Dann kann  man in diese Lücke quasi eine beliebige Annotation in
ihrer "inline Form" dort platziert werden. Beispiel:

  ... auf Abbdilung \0 ist zu sehen ...

Dann gibt es dazu eine Annotation, die den entsprechenden Query bzw.
das Ergebnis des Query entählt. (Mit der Information, dass dies an diese
Position gezeichnet werden soll).

Vorteil wäre, man ist flexibler. Denn man könnte z.B. dem Dokument sagen,
dass alle Gewichtsmaßeinheiten auf Kilogramm umgerechnet werden sollen...

# Annotationen und Properties

Im Prinzip kann man in den Annotations-Informationen (from, to, etc.)
auch Properties bzw. Attribute ablegen.
Das heißt dass Annotationen Properties mitbringen.
Im essentiellen entsprechen Properties den Annotationen.

Wenn ein Dokument-Element einen Deep-Link enthält,
muss die Modelllaufzeit eines anderen Dokument-Elements mit einem
Query befragt werden.
Zur besseren Performance und um nicht von der Verfügbarkeit der
Modelllaufzeit abhänig zu sein, ist es sinnvoll diese Query+Antwort dann als
Annotation abzulegen (Cache).
