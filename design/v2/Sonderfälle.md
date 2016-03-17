Figure ist ein schöner Sonderfall.

figure/figure png "binary png"
figure/figure internallink "png/sha-2:e3b0c4429"  // diese Internal Links müssten im Falle des Teilen mit anderen DocElem-Stores "verallgemeinert" werden bzw. übersetzt/gemapped werden auf die Struktur des anderen Stores...
figure/figure internalTripleLink "png/uuid:15ff hasVersion png/sha-2:ca495991b"


Die zweite Variante braucht dann noch einen zusätzlichen Lookup vom Viewer, also weniger Performant. Aber in diesem Modell ist beides möglich!

PNG wäre ein weiterer Sonderfall.

png/sha-2:e3b0c4429
  "_"
    png/png png "binary png"
    isVersionOf internallink "png/uuid:15ff"

Denn hier wäre die UID der Fingerprint und damit auch gleichzeitig die Version. Wenn man aber das Bild verändert und es quasi noch das selbe anzeigt, kann man diese ggf. mit einer Annotation in Relation setzen.

// Ein reines Meta-Element
// Neuste Version kann mit Timestamp bestimmt werden
png/uuid:15ff
  "_"
    hasVersion internallink "png/sha-2:e3b0c4429"  // und deswegen sind fingerprints wichtig...
    hasVersion internallink "png/sha-2:ca495991b"


Neue Erkenntnis?:
Solch eine Mechanik ähnelt sehr stark der Topologie bzw. ist ja an sich eine Art von Topologie. Diese betrifft das DokumentElement aber quasi nur intern bzw. aus Versionierungsgründen. Ist Versionierung nur ein Spezialfall der Topologie, also Versionstopologie?
