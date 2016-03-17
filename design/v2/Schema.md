# Definition von Paragraph v1

paragraph ; "" ; descr ; Ein Paragrap ist ein aufgeschriebener Gedanke
paragraph ; "" ; version ; paragraph@v1
paragraph ; "" ; version ; paragraph@v2

paragraph@v1 ; "" ; scaiImpl ; de.fraunhofer.scai.Paragraph v7.0
paragraph@v1 ; "" ; owlClass ; http://purl.org/spar/doco/Paragraph v1.3


## Defintion der Paragraph v1 Attribute

paragraph/paragraph ; "" ; descr ; Main Attribut of paragraph.
paragraph/rhetorical ; "" ; descr ; Rhetorische Bedeutung des Paragraph.

# Eine Instanz von Paragraph

Version is calulated with hash over main properties (paragraph/*)...

paragraph/uuid:15ff
  "_"
    hasVersion InternalReference "paragraph/uuid:15ff@v1"
    hasVersion InternalReference "paragraph/uuid:15ff@v2"
    // Neuste Version kann einfach via Timestamp des Records rausgefunden werden

paragraph/uuid:15ff@v1
  "_"
    paragraph/paragraph FreeText f1 ts100 "A paragraph"
    paragraph/rhetorical FreeText f3 ts099 "discussion"
    paragraph Specification "de.fraunhofer.scai.paragraph@v1"
  "run/belief-15"
    nne/mesh:MDC002854@v4 annot "position=[paragraph/paragraph:10:15],internallink=[nne/mesh:MDC002854@v4]"
    // Wenn man das DocElem serialisiert (z.B. zum Teilen), dann muss zu jedem Attribut auch noch die Spezifikation genannt werden (run, nne), damit der geneigte Leser das auch wieder vollumfänglich interpretieren kann.

paragraph/uuid:15ff@v2
  "_"
    paragraph/paragraph FreeText f2 ts110 "A paragraph with a change"
    paragraph/rhetorical FreeText f3 ts099 "discussion"
    paragraph SpecVersion "paragraph@v1"

Mantra: Alles ist ein DokumentElement und Annotations (bzw. Attribute) are cheap.

Frage: Brauchen wir die Fingerprints überall noch? Diese werden doch quasi verwendet um die Versionsnummer aufzubauen? Diese werden gebraucht, damit z.B. gleiche Annotationen nur mit unterschiedlichen Positionen vorkommen können. (Wobei man sicher auch diese Annotationen auch zu einem Positions-Modell zusammenfassen kann! => Muss dann vorher aggregiert werdne und dann abgespeichert => Vorteil ist dass man einen Record spart und dass man die ganzen Fingerprints spart...)
Vielleicht sollte man auch einfache die Möglichkeit haben, die Fingerprints ein und aus zu schalten. Beispielsweise beim Import kann man bei einem Attribut sagen, dass er hier kein Fingerprint machen soll oder immer den gleichen Vergeben. -> Dann behält Accumulo nur den Record mit dem neusten Timestamp.
Ob mit oder ohne Fingerprint sollte vom Programm anhand des Attributes entschieden werden können. (Opt-In also z.B. nne-Annotationen wollen einen fingerprint, damit mehere Typ-gleiche Annotationen koexistieren können.)

Calling paragraph/uuid:15ff is Range.prefix(paragraph/uuid:15ff),
and the one with newest record is showed.
Oder man macht ein paragraph/uuid:15ff docelem, wo alle Versionen verzeichnet sind und kann dort aus der neusten Versionsannotation auf die neuste Version schließen und wird von einem Viewer automatisch weitergeleitet.
DocElems mit der gleichen Konfiguration, bekommen wieder die gleiche Versionsnummer, aber jedoch mit neuen Timestamps!

Im Topologie-Index wird dann auf die exakten Versionen verwiesen, daher kann das mit einem Batch-Scanner auch relativ schnell rausgeholt werden? (Accumulo, S.114)

Vergleich zweier Layer (Johannes): Range.prefix(paragraph/) .fetchColumnFamily("run/belief-15") holt alle paragraphs mit diesem run raus. Dann müsste noch ein zweiter Scan mit z.B. run/belief-16 laufen. Dann kann man diese vergleichen.

Alle DocElems eines Types: Range.prefix(paragraph/) .fetchColumnFamily("_"). Damit würden halt alle DocElem-Versionen in ihrem 0-Layer (Model Content) kommen. Dann müsste man manuell z.B. auf Serverseite noch jeweils nach neuster Version filtern (z.B. nach dem @ immer die mit dem neusten Timestamp).
Man müsste ausprobieren wie Performant es ist wenn man z.B. aus dem Topo-Index ein Dokument mit dem Batch-Scanner rausholt.
Was auch möglich ist, wenn alle Dokumente den gleichen Versionstag erhalten, z.B. bei PubMedAbstracts macht das sinn wenn die in der Form header/pmid:@2016:121212 wären -> nur so kann man nach Prefix scannen. Oder Range(header/pmid:0@2016, header/pmid:25000000@2016), was aber nur bei z.B. PMIDs gut funktionieren würde. (Was hier erstmal reicht)

Annotation-Index wird  mit Layer und Attributen aufgebaut.
ColFam.prefix("run/belief-15!nne/mesh:MDC002854@v4")
oder allgemeiner
ColFam.prefix("run/belief-15!nne/mesh:MDC002854")
oder nur alle NNEs mit MeSH
ColFam.prefix("run/belief-15!nne/mesh:")
oder nur alles im Layer
ColFam.prefix("run/belief-15!")
