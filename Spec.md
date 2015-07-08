# Basis API

Jede Instanz (z.B. Pipelet, Besucher etc.) erhält ihren eigenen `store`-Aktor.

    // ask pattern
    docelem = store ? Get(uuid)

Jede Nachricht kann noch von `Version(msg, nr|id|date)` ummantelt werden,
falls eine spezifische Version angefordert werden soll.

    docelem ! Edit(change)

## Templating (Projektionen, Transformatoren, Generatoren)

Das Templating-Modul sollte mittelfristig in einen extra Templating Service
ausgelagert werden!

    // ask pattern
    projectedModel = docelem ? Projection(Html)
    projectedModel = docelem ? Projection(Raw)
    projectedModel = docelem ? Version(Projection(Html), "ef8i")

Templates sind selbst wieder Dokumentelemente.
Das Templating-Modul erhält das Template und das Modell bzw.
die Modell-Attribute des Ziel-Dokumentelements, um daraus
die gewünschte Projektion zu erstellen.

Irgendwo muss konfiguriert werden, welche Dokumentelement-Typen
mit welchen Template-Dokumentelementen gerendert werden können.
Man kann darüber nachdenken, diese Mappings ebenfalls
als Dokumentelement(e) zu hinterlegen.

## Annotationen

Eine Annotation ist eine Beziehung von zu einem Dokumentelement.
Jede Annotation hat einen Zweck.
Jede Annotation kann ein eine bestimmte Position gesetzt werden,
und ggf. weitere Eigenschaften besitzen.
Jede Annotation sollte einem (Annotations-)Layer zugeornet werden.
Ein Annotationslayer ist wieder ein (Meta-)Dokumentelement.
Darüber kann ermittelt werden, wer diese Annotationen geschrieben hat;
ein Satz unerwünschter Annotationen können leicht entfernt werden;
verschiedene Versionen eines Dokumentelements können eigene Annotationen besitzen.

Beispiel: In einem Paragraph wird ein Teil des Textes mit einem Kommentar versehen.
Zweck ist die Kommentierung und der Kommentar selbst kann z.B. wieder ein Paragraph-Dokumentelement sein.

    docelem ! GetAnnotations(query)
    docelem ! AnnotateWith(uuid|docelem, purpose, position, layer, props)

(In OrientDB kann man `purpose` dann als Kantentyp modellieren.)

Beispiele für `query` sind:

  * gib mir alle Annotationen mit Provanance name=Sven
  * gib mir alle Annotationen
  * gib mir die ersten 10 Annotationen vom Typ Konzept namespace="HGNC"

## Provenance

Provenance Kanten zeigt immer auf einen Urheber (Autoren, Co-Autoren, Programme).
Der Urheber wird durch ein Dokumentelement repräsentiert,
z.B. ein Dokumentelement vom Typ Person mit z.B. einem vCard-Modell.

    docelem ! GetProvanance(query)
    docelem ! OfProvenance(creator, position)

Im Prinzip ist die Provenance nur ein Spezialfall einer Annotation.
Es ist eine vom System vordefinierte Annotation mit dem Zweck
die Herkunft zu annotieren.

Weiteres Attribut könnte `importer` und `source` sein.
Beispiel: DocElem -prov-> creator: Author Name, source: Medline, importer: shodapp...

??? Der Prototyp wird zeigen, ob Provenance nicht sogar als eigener DocElem-Typ modelliert werden sollte.
Vermutlich ist das sogar sinnvoller!

## Topologie (Korpus)

Im Prinzip sind die Topologie-Kanten ebenfalls wieder nur Spezialfälle
der Annotation.

Die Topologie-Kanten zeigen immer auf andere Dokumentelemente.
Es gibt zwei Arten von Topologie-Kanten:

  * First Child
  * Next

Zudem gibt es noch ein Dokumentelement vom Typ `Root` oder `Document`,
welches den Beginn eines Dokumenten-Korpus darstellt.
Zudem können dort noch weitere Meta-Informationen zum Dokument gesammelt werden.
(Im DocElem-Modell, den Modell-Attributen oder als Annotationen)

Damit kann ein abstrakter Syntaxbaum repräsentiert werden.
Dadurch, dass ein Dokumentelement eventuell in mehreren Dokument-Korpera
vorkommen kann, gibt es noch weitere Properties auf den Kanten:

  * belongs_to: uuid des Root-DocElems
  * is_cited: true / version-nr (des zitierten DocElem)
  * layer: id

Wenn `is_cited` aktiv ist, gilt das nächste Dokumentelement als zitiert
aus einem anderen Dokument.
Ein aktives `is_cited` erfordert, dass auf eine bestimmte Version des
Dokumentelements zitiert wird.

Man kann natürlich auch von jedem Dokumentelement ausgehend die Topologie
ablaufen. Nur muss dann dazu die Angabe gemacht werden, zu welchem
Dokument die Topologie angezeigt werden soll.
Das ist nützlich wenn man z.B. einen Teilausschnitt eines Dokuments
betrachten will.

# Versionsmanagement

Dokumentelemente mit Versionen sind quasi mehrdimensionale Dokumentelemente.
Jedes Dokumentelement hat einen Vektor/Liste wo die Vergangenheit des
Modells gespeichert ist (Verweis auf Modell-Id oder Modell selbst).
Der HEAD der Liste ist immer die aktuellste Version.
Ein named-tag ist ein Verweis auf eine spezifische Version (List-Item),
die ein Autor gerne als einen signifikaten Punkt markiert.

Es sollte vorgesehen sein, dass man mehrere Annotationslayer
gleichzeitig anschauen kann. Eventuell auch Annotationslayer einer
anderen Version sollten betrachtbar sein. (Wenn man z.B. den Annotated String
(Positionen werden Konstant) implementiert hat, kann das nützlich sein!)

Versionierung der Annotationen (also der Kanten) geschieht über
die Annotationslayer.
Ein Annotationslayer ist einer spezifischen Version des Dokumentelements
zugeordnet.
Wird z.B. eine Version eines gesamten Dokuments "getaggt",
so werden die betroffenen Annotationslayer am besten eingefrohren
und damit nicht mehr veränderbar gemacht.

## Konflikte über Annotationen behandeln

Wenn Autor und Co-Autoren gleichzeitig am gleichen Dokumentelement arbeiten,
kann es zu Konflikten kommen.

Beispiel:

  * Ursprung Model: "Hallo"
  * Autoren bearbeiten gleichzeitig:
    * A: "Hallo" -> "Hallo Welt"
    * B: "Hallo" -> "Hallo schöne Welt"
  * Es tritt ein Konflikt auf!
  * Statt eine neue Version für das Dokumentelement herauszugeben,
    wird das langsamere Update (hat also MVC Error) als ein neues
    (temporäres) Dokumentelement verpackt und als z.B. `resolve_conflict`
    an das Dokumentelement annotiert. (z.B. sogar mit Merge-Vorschlägen;
    im Prinzip kann das wie "kurieren" behandelt werden.)

## Konflikte Live behandeln

Der Editor des Dokumentelements bringt eine Live-Ansicht mit und übernimmt die Merges.
Bei Text könnte es so aussehen, dass alle Autoren sich den gleichen Dokumentelement-Aktor
teilen und diesem Nachrichten schicken.
Sobald der Aktor Änderungen feststellt, werden alle anderen verbundenen Autoren
sofort informiert. Bei Text zeigt Google Docs wie das funktionieren kann.

# Nutzermanagement

Die feinst granulierten Rechte kann ich mir bei einem Dokument wie folgt vorstellen:

  * None
  * Read
    * cite
    * annotate (comments, tags)
  * Write
    * proofread (Lektor)
    * curate (Domain-Experte)
    * edit (Autor, Co-Autor)

Diese Rechte sollen auf Dokumentelement-Ebene ausgeführt werden.
Rechte zeigen immer auf eine Gruppe von Personen.

## API

Den `token` sollte der Benutzer zuvor von einem speziellen
Authentification Service besorgen.

    store ! Auth(token)

Darauf hin kann der `store` ebenfalls einen Authentification Service anrufen,
um den `token` auf Gültigkeit zu prüfen und zugehörigen Benutzer zu erfahren.

Fordert der Benutzer ein Dokumentelement an auf das er keinen Lesezugriff hat,
antwortet das Dokumentelement statt z.B. mit dem Inhalt mit "no read permission on ${uuid}".

    docelem ! ChRights(group, "+e")

## Datenstruktur

Es gibt Benutzer-Dokumentelemente, z.B. haben diesen ein vCard-Modell.
Es gibt Gruppen-Dokumentelemente welche Benutzer annotiert haben.
Die erste Gruppe die es gibt ist "Everybody" zu der jeder Benutzer (auch anonyme) gehört.

Kanten auf Dokumentelement mit den Eigenschaften:

  * DocElem `-- rights -->` Gruppe
  * Properties wie o.g.
  * Diese Kanten werden nicht versioniert! Es gilt immer nur die aktuellste;
    Sonst könnte man z.B. eine Veröffentlichung nicht mehr Rückgängig machen!

??? Ist es sinnvoll die Rechte getrennt von den eigentlichen Daten zu halten?

# Deduplizierung

# Verteiltes System
## Synchronisierung
Man soll den DocElem Store z.B. auf ein Laptop laden können und daran offline arbeiten, wenn der Laptop wieder online ist, kann es sich synchronisieren.
## Replikation (Master-Master)
Der DocElem Store sollte als Service (Hintergrundprogramm) konzipiert sein. Jede neue Instanz des Hintergrundprogramms sollte einen weiteren Knoten im Cluster eröffnen / repräsentieren.
