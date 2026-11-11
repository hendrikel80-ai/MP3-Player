package de.schach.training

import de.schach.engine.Piece

/** Ein Zug einer Eröffnung in SAN (englische Buchstaben: N, B, R, Q, K) mit Erklärung. */
data class OpeningMove(val san: String, val note: String)

data class Opening(
    val id: String,
    val name: String,
    /** Seite, die der Lernende übt */
    val side: Int,
    val intro: String,
    val moves: List<OpeningMove>,
)

/** Mattaufgabe: Weiß bzw. die Seite am Zug setzt in [mateIn] Zügen matt. */
data class Puzzle(
    val id: String,
    val title: String,
    /** Hauptfigur(en) des Mattbilds, für die Gruppierung */
    val pieces: String,
    val fen: String,
    val mateIn: Int,
    val hint: String,
    val explanation: String,
)

/** Mattführung: König + Figur(en) gegen den nackten König; der Computer verteidigt. */
data class MateLesson(
    val id: String,
    val title: String,
    val difficulty: String,
    val fen: String,
    val intro: String,
    val tips: List<String>,
)

private fun m(san: String, note: String) = OpeningMove(san, note)

object TrainingData {

    val openings = listOf(
        Opening(
            "italian", "Italienische Partie", Piece.WHITE,
            "Eine der ältesten Eröffnungen: schnelle Entwicklung, der Läufer zielt auf f7. Ideal zum Lernen der Grundprinzipien.",
            listOf(
                m("e4", "Weiß besetzt das Zentrum und öffnet Wege für Dame und Läufer."),
                m("e5", "Schwarz hält im Zentrum dagegen."),
                m("Nf3", "Der Springer entwickelt sich und greift den Bauern e5 an."),
                m("Nc6", "Schwarz deckt e5 mit dem Springer."),
                m("Bc4", "Der Läufer zielt auf f7 – den schwächsten Punkt bei Schwarz, nur vom König gedeckt."),
                m("Bc5", "Schwarz antwortet spiegelbildlich und zielt auf f2."),
                m("c3", "Bereitet d4 vor, um das Zentrum mit Bauern zu besetzen."),
                m("Nf6", "Schwarz entwickelt sich und greift e4 an."),
                m("d3", "Deckt e4 in Ruhe – die ruhige Variante (Giuoco Pianissimo)."),
                m("d6", "Schwarz stützt e5 und öffnet den Läufer c8."),
                m("O-O", "Kurze Rochade: der König ist sicher, der Turm kommt ins Spiel."),
                m("O-O", "Auch Schwarz rochiert. Beide Seiten haben sich gesund entwickelt."),
            ),
        ),
        Opening(
            "ruy", "Spanische Partie", Piece.WHITE,
            "Die „Königin der Eröffnungen“: Weiß übt mit dem Läufer langfristig Druck auf den Verteidiger von e5 aus.",
            listOf(
                m("e4", "Zentrum besetzen."),
                m("e5", "Schwarz hält dagegen."),
                m("Nf3", "Entwicklung mit Angriff auf e5."),
                m("Nc6", "Schwarz deckt e5."),
                m("Bb5", "Der Läufer greift den Springer an, der e5 deckt – indirekter Druck aufs Zentrum."),
                m("a6", "Schwarz stellt dem Läufer die Frage."),
                m("Ba4", "Der Läufer zieht sich zurück und hält den Druck aufrecht."),
                m("Nf6", "Entwicklung mit Angriff auf e4."),
                m("O-O", "Weiß rochiert – e4 ist indirekt gedeckt."),
                m("Be7", "Schwarz bereitet die eigene Rochade vor."),
                m("Re1", "Der Turm deckt e4."),
                m("b5", "Schwarz vertreibt den Läufer endgültig."),
                m("Bb3", "Auf b3 zielt der Läufer weiter auf f7."),
                m("d6", "Stützt e5 und öffnet den Läufer c8."),
                m("c3", "Bereitet d4 vor und schafft dem Läufer ein Rückzugsfeld auf c2."),
                m("O-O", "Schwarz rochiert – die Hauptstellung der Spanischen Partie."),
            ),
        ),
        Opening(
            "scotch", "Schottische Partie", Piece.WHITE,
            "Weiß öffnet das Zentrum sofort mit d4 – direkt, aktiv und leicht zu verstehen.",
            listOf(
                m("e4", "Zentrum besetzen."),
                m("e5", "Schwarz hält dagegen."),
                m("Nf3", "Angriff auf e5."),
                m("Nc6", "e5 wird gedeckt."),
                m("d4", "Weiß öffnet sofort das Zentrum."),
                m("exd4", "Schwarz nimmt den Bauern."),
                m("Nxd4", "Der Springer schlägt zurück und steht stark im Zentrum."),
                m("Nf6", "Schwarz greift e4 an."),
                m("Nxc6", "Tausch – Schwarz muss mit einem Bauern zurückschlagen."),
                m("bxc6", "Schwarz bekommt Doppelbauern, aber mehr Zentrumsbauern."),
                m("e5", "Der Bauer vertreibt den Springer f6."),
                m("Qe7", "Die Dame greift e5 an."),
                m("Qe2", "Weiß deckt e5."),
                m("Nd5", "Der Springer weicht auf ein zentrales Feld aus."),
                m("c4", "Weiß vertreibt den Springer erneut und gewinnt Raum."),
            ),
        ),
        Opening(
            "qgd", "Damengambit", Piece.WHITE,
            "Klassische Eröffnung mit 1.d4: Weiß bietet den c-Bauern an, um Schwarz aus dem Zentrum zu locken.",
            listOf(
                m("d4", "Weiß besetzt das Zentrum mit dem Damenbauern."),
                m("d5", "Schwarz hält dagegen."),
                m("c4", "Das Damengambit: Weiß greift d5 von der Seite an."),
                m("e6", "Schwarz lehnt ab und stützt d5 (Abgelehntes Damengambit)."),
                m("Nc3", "Mehr Druck auf d5."),
                m("Nf6", "Deckt d5 ein weiteres Mal."),
                m("Bg5", "Fesselt den Springer an die Dame."),
                m("Be7", "Hebt die Fesselung auf."),
                m("e3", "Öffnet den Läufer f1 und stützt d4."),
                m("O-O", "Schwarz bringt den König in Sicherheit."),
                m("Nf3", "Entwicklung Richtung Zentrum."),
                m("h6", "Schwarz stellt dem Läufer die Frage."),
                m("Bh4", "Weiß hält die Fesselung aufrecht."),
            ),
        ),
        Opening(
            "london", "Londoner System", Piece.WHITE,
            "Ein solides System, das fast gegen alles funktioniert: Läufer nach f4, Bauern nach c3–d4–e3.",
            listOf(
                m("d4", "Zentrum besetzen."),
                m("d5", "Schwarz hält dagegen."),
                m("Bf4", "Der Läufer kommt heraus, bevor e3 ihn einsperrt."),
                m("Nf6", "Schwarz entwickelt sich."),
                m("e3", "Stabile Bauernkette, der Läufer f1 wird frei."),
                m("e6", "Schwarz stützt d5."),
                m("Nf3", "Entwicklung."),
                m("c5", "Schwarz greift das Zentrum an."),
                m("c3", "Die typische Londoner Pyramide c3–d4–e3."),
                m("Nc6", "Mehr Druck auf d4."),
                m("Nbd2", "Der Springer geht nach d2, um c3 nicht zu blockieren."),
                m("Bd6", "Schwarz bietet den Läufertausch an."),
                m("Bg3", "Weiß weicht aus und behält die Diagonale."),
                m("O-O", "Schwarz rochiert."),
                m("Bd3", "Der Läufer zielt auf den schwarzen Königsflügel."),
            ),
        ),
        Opening(
            "english", "Englische Eröffnung", Piece.WHITE,
            "Weiß beginnt mit dem c-Bauern und kontrolliert das Zentrum von der Seite.",
            listOf(
                m("c4", "Englisch: Weiß kontrolliert d5 von der Seite."),
                m("e5", "Schwarz besetzt das Zentrum."),
                m("Nc3", "Mehr Kontrolle über d5."),
                m("Nf6", "Schwarz entwickelt sich."),
                m("g3", "Bereitet das Fianchetto des Läufers vor."),
                m("d5", "Schwarz öffnet das Zentrum."),
                m("cxd5", "Weiß tauscht."),
                m("Nxd5", "Der Springer schlägt zurück."),
                m("Bg2", "Der Läufer drückt auf d5 und die lange Diagonale."),
                m("Nb6", "Der Springer weicht aus."),
                m("Nf3", "Entwicklung mit Angriff auf e5."),
                m("Nc6", "Schwarz deckt e5."),
                m("O-O", "Weiß rochiert."),
                m("Be7", "Schwarz bereitet die Rochade vor."),
            ),
        ),
        Opening(
            "sicilian", "Sizilianische Verteidigung", Piece.BLACK,
            "Die beliebteste Antwort auf 1.e4: Schwarz spielt asymmetrisch und kämpft um Gewinnchancen.",
            listOf(
                m("e4", "Weiß eröffnet mit dem Königsbauern."),
                m("c5", "Sizilianisch: Schwarz kämpft von der Seite um das Feld d4."),
                m("Nf3", "Weiß bereitet d4 vor."),
                m("d6", "Kontrolliert e5 und öffnet den Läufer c8."),
                m("d4", "Weiß öffnet das Zentrum."),
                m("cxd4", "Schwarz tauscht einen Flügelbauern gegen einen Zentrumsbauern."),
                m("Nxd4", "Der Springer schlägt zurück."),
                m("Nf6", "Angriff auf e4."),
                m("Nc3", "Weiß deckt e4."),
                m("a6", "Najdorf-Variante: kontrolliert b5 und bereitet …e5 oder …b5 vor."),
            ),
        ),
        Opening(
            "french", "Französische Verteidigung", Piece.BLACK,
            "Solide und strategisch: Schwarz baut eine feste Bauernkette und greift das weiße Zentrum später an.",
            listOf(
                m("e4", "Weiß eröffnet mit dem Königsbauern."),
                m("e6", "Französisch: Schwarz bereitet …d5 vor."),
                m("d4", "Weiß baut das Zentrum aus."),
                m("d5", "Schwarz greift e4 an."),
                m("e5", "Vorstoßvariante: Weiß gewinnt Raum."),
                m("c5", "Schwarz greift die Bauernkette an ihrer Basis d4 an."),
                m("c3", "Weiß stützt d4."),
                m("Nc6", "Mehr Druck auf d4."),
                m("Nf3", "Weiß deckt d4 ein weiteres Mal."),
                m("Qb6", "Die Dame greift d4 und b2 gleichzeitig an."),
            ),
        ),
        Opening(
            "carokann", "Caro-Kann-Verteidigung", Piece.BLACK,
            "Sehr solide: Schwarz spielt …d5, ohne seinen hellfeldrigen Läufer einzusperren.",
            listOf(
                m("e4", "Weiß eröffnet mit dem Königsbauern."),
                m("c6", "Caro-Kann: bereitet …d5 vor."),
                m("d4", "Weiß baut das Zentrum aus."),
                m("d5", "Schwarz greift e4 an."),
                m("Nc3", "Weiß deckt e4."),
                m("dxe4", "Schwarz tauscht im Zentrum."),
                m("Nxe4", "Der Springer schlägt zurück."),
                m("Bf5", "Der Läufer kommt aktiv heraus und greift den Springer an."),
                m("Ng3", "Der Springer greift den Läufer an."),
                m("Bg6", "Rückzug auf ein sicheres Feld."),
                m("h4", "Weiß droht h5 mit Läuferfang."),
                m("h6", "Schwarz schafft dem Läufer ein Rückzugsfeld auf h7."),
                m("Nf3", "Entwicklung."),
                m("Nd7", "Bereitet …Sgf6 vor und verhindert Se5."),
            ),
        ),
        Opening(
            "scandi", "Skandinavische Verteidigung", Piece.BLACK,
            "Schwarz greift e4 sofort an – einfach zu lernen und mit klaren Plänen.",
            listOf(
                m("e4", "Weiß eröffnet mit dem Königsbauern."),
                m("d5", "Skandinavisch: Schwarz greift e4 sofort an."),
                m("exd5", "Weiß schlägt."),
                m("Qxd5", "Die Dame schlägt zurück."),
                m("Nc3", "Entwicklung mit Tempo gegen die Dame."),
                m("Qa5", "Die Dame weicht auf ein aktives, sicheres Feld aus."),
                m("d4", "Weiß besetzt das Zentrum."),
                m("Nf6", "Schwarz entwickelt sich."),
                m("Nf3", "Weiß entwickelt sich."),
                m("c6", "Gibt der Dame ein Rückzugsfeld und kontrolliert b5 und d5."),
                m("Bc4", "Weiß entwickelt den Läufer."),
                m("Bf5", "Der Läufer kommt heraus, bevor …e6 ihn einsperrt."),
            ),
        ),
        Opening(
            "kid", "Königsindische Verteidigung", Piece.BLACK,
            "Gegen 1.d4: Schwarz lässt Weiß ein großes Zentrum bauen und greift es später an.",
            listOf(
                m("d4", "Weiß eröffnet mit dem Damenbauern."),
                m("Nf6", "Flexible Entwicklung."),
                m("c4", "Weiß gewinnt Raum."),
                m("g6", "Bereitet das Fianchetto vor."),
                m("Nc3", "Weiß entwickelt sich."),
                m("Bg7", "Der Läufer wirkt auf der langen Diagonale."),
                m("e4", "Weiß baut ein großes Zentrum."),
                m("d6", "Schwarz lässt das zu und stützt …e5."),
                m("Nf3", "Weiß entwickelt sich."),
                m("O-O", "Schwarz rochiert."),
                m("Be2", "Weiß bereitet die Rochade vor."),
                m("e5", "Der typische Gegenstoß im Zentrum."),
            ),
        ),
        Opening(
            "scholar", "Schäfermatt abwehren", Piece.BLACK,
            "Viele Anfänger versuchen das Schäfermatt (Dxf7#). Hier lernst du, es sicher abzuwehren.",
            listOf(
                m("e4", "Weiß eröffnet mit dem Königsbauern."),
                m("e5", "Schwarz hält dagegen."),
                m("Qh5", "Die Dame kommt früh heraus und greift e5 an."),
                m("Nc6", "Deckt e5 und entwickelt eine Figur."),
                m("Bc4", "Jetzt droht Dxf7# – das Schäfermatt!"),
                m("g6", "Vertreibt die Dame und verhindert das Matt."),
                m("Qf3", "Neue Drohung auf f7!"),
                m("Nf6", "Der Springer sperrt die f-Linie – f7 ist sicher und Schwarz entwickelt sich mit Tempo."),
            ),
        ),
    )

    val puzzles = listOf(
        Puzzle(
            "backrank", "Grundreihenmatt", "Turm", "6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1", 1,
            "Der König ist hinter seinen eigenen Bauern eingesperrt.",
            "Td8# – der König kann nicht nach vorne fliehen, weil seine eigenen Bauern im Weg stehen.",
        ),
        Puzzle(
            "arabian", "Arabisches Matt", "Turm + Springer", "7k/R7/5N2/8/8/8/8/6K1 w - - 0 1", 1,
            "Turm und Springer arbeiten zusammen – der Springer deckt den Turm.",
            "Th7# – der Turm gibt Schach, der Springer deckt ihn und das Feld g8.",
        ),
        Puzzle(
            "smothered", "Ersticktes Matt", "Springer", "6rk/6pp/8/6N1/8/8/8/6K1 w - - 0 1", 1,
            "Der König ist von seinen eigenen Figuren umzingelt.",
            "Sf7# – der König kann nicht fliehen, weil alle Nachbarfelder von eigenen Figuren besetzt sind.",
        ),
        Puzzle(
            "kiss", "Kuss des Todes", "Dame", "6k1/5p1p/8/8/8/2Q5/1B6/6K1 w - - 0 1", 1,
            "Die Dame direkt vor dem König – aber gedeckt.",
            "Dg7# – die Dame steht direkt vor dem König und wird vom Läufer gedeckt.",
        ),
        Puzzle(
            "queenedge", "Damenmatt am Rand", "Dame + König", "7k/8/6K1/8/8/8/Q7/8 w - - 0 1", 1,
            "Dein König nimmt dem schwarzen König schon die Fluchtfelder.",
            "Da8# – der eigene König deckt die Fluchtfelder, die Dame gibt Schach.",
        ),
        Puzzle(
            "bishop", "Läufermatt auf der langen Diagonale", "Läufer", "6bk/7p/8/8/5B2/8/8/6K1 w - - 0 1", 1,
            "Die lange Diagonale a1–h8 führt direkt zum König.",
            "Le5# – der König ist eingesperrt und der schwarze Läufer kann die dunklen Felder nicht blockieren.",
        ),
        Puzzle(
            "boden", "Bodens Matt", "Zwei Läufer", "2kr4/3n4/8/8/5B2/8/4B3/6K1 w - - 0 1", 1,
            "Zwei Läufer auf sich kreuzenden Diagonalen.",
            "La6# – der Läufer f4 deckt b8 und c7, der Läufer a6 gibt Schach.",
        ),
        Puzzle(
            "pawn", "Bauernmatt", "Bauer", "7k/5K1p/6P1/8/8/8/8/8 w - - 0 1", 1,
            "Auch ein Bauer kann mattsetzen!",
            "g7# – der Bauer gibt Schach und wird von deinem König gedeckt.",
        ),
        Puzzle(
            "ladder", "Treppenmatt", "Zwei Türme", "6k1/8/8/8/8/8/R7/1R4K1 w - - 0 1", 2,
            "Ein Turm sperrt eine Reihe ab, der andere gibt auf der nächsten Reihe Matt.",
            "Ta7! sperrt die 7. Reihe ab, danach setzt Tb8 matt.",
        ),
        Puzzle(
            "queenrook", "Dame und Turm", "Dame + Turm", "6k1/8/8/8/8/8/8/QR4K1 w - - 0 1", 2,
            "Wie beim Treppenmatt: erst eine Reihe absperren.",
            "Da7! sperrt die 7. Reihe ab, danach setzt Tb8 matt.",
        ),
        Puzzle(
            "deflection", "Grundreihe mit Ablenkung", "Zwei Türme", "3q2k1/5ppp/8/8/8/8/4R3/4R1K1 w - - 0 1", 2,
            "Die schwarze Dame ist der einzige Verteidiger der Grundreihe.",
            "Te8+! Dxe8 Txe8# – die Dame wird von der Grundreihe weggelockt.",
        ),
        Puzzle(
            "philidor", "Philidors ersticktes Matt", "Dame + Springer", "4r2k/6pp/7N/3Q4/8/8/8/6K1 w - - 0 1", 2,
            "Opfere die Dame so, dass sich der König selbst einsperrt.",
            "Dg8+!! Txg8 Sf7# – nach dem Damenopfer erstickt der König zwischen seinen eigenen Figuren.",
        ),
    )

    val mateLessons = listOf(
        MateLesson(
            "kq", "Matt mit der Dame", "Leicht", "8/8/3k4/8/8/8/8/Q3K3 w - - 0 1",
            "König und Dame gegen König – das wichtigste Grundmatt. Ziel: den König an den Rand drängen und mattsetzen.",
            listOf(
                "Stell die Dame einen Springerzug vom gegnerischen König entfernt – so wird sein Bereich immer kleiner.",
                "Folge dem König mit der Dame im Springerabstand, bis er am Rand steht.",
                "Achtung Patt! Lass dem König immer mindestens ein Feld, solange du nicht Schach gibst.",
                "Hol jetzt deinen König heran – die Dame allein kann nicht mattsetzen.",
                "Matt: Dame gibt am Rand Schach, dein König deckt die Fluchtfelder.",
            ),
        ),
        MateLesson(
            "krr", "Treppenmatt mit zwei Türmen", "Leicht", "8/8/3k4/8/8/8/8/R3K2R w - - 0 1",
            "Zwei Türme brauchen keinen König: Sie schieben den König Reihe für Reihe an den Rand.",
            listOf(
                "Ein Turm sperrt eine Reihe ab, der andere gibt auf der nächsten Reihe Schach.",
                "Abwechselnd ziehen – wie eine Treppe – bis der König am Rand steht.",
                "Kommt der König einem Turm zu nahe, zieh den Turm weit weg auf die andere Seite des Bretts.",
            ),
        ),
        MateLesson(
            "kr", "Matt mit dem Turm", "Mittel", "8/8/3k4/8/8/8/8/R3K3 w - - 0 1",
            "König und Turm gegen König: Hier muss dein König kräftig mithelfen.",
            listOf(
                "Der Turm sperrt den König in ein Rechteck ein – mach das Rechteck Schritt für Schritt kleiner.",
                "Bring deinen König in Opposition (direkt gegenüber, ein Feld dazwischen).",
                "Gib in der Opposition mit dem Turm Schach – so wird der König eine Reihe zurückgedrängt.",
                "Wenn der Turm angegriffen wird: zieh ihn ans andere Ende der Reihe.",
                "Am Rand: Opposition + Turmschach = Matt.",
            ),
        ),
        MateLesson(
            "kbb", "Matt mit zwei Läufern", "Mittel", "8/8/3k4/8/8/8/8/2B1KB2 w - - 0 1",
            "Zwei Läufer nebeneinander bilden eine Mauer, die der König nicht überqueren kann.",
            listOf(
                "Stell die Läufer nebeneinander in die Brettmitte – zusammen versperren sie diagonale Bänder.",
                "Bring deinen König heran, er hilft beim Abdrängen.",
                "Treib den König erst an den Rand, dann in eine Ecke.",
                "Matt gelingt nur in der Ecke. Achte auf Patt!",
            ),
        ),
        MateLesson(
            "kbn", "Matt mit Läufer und Springer", "Schwer", "8/8/3k4/8/8/8/8/1N2KB2 w - - 0 1",
            "Das schwierigste Grundmatt. Matt geht nur in einer Ecke mit der Farbe deines Läufers (hier: h1 oder a8).",
            listOf(
                "Treib den König zuerst mit König, Läufer und Springer an den Rand.",
                "Steht er in der „falschen“ Ecke (a1 oder h8), muss er entlang des Randes in die richtige Ecke getrieben werden.",
                "Der Springer deckt die Felder, die der Läufer nicht erreichen kann (andere Farbe).",
                "Du hast 50 Züge – arbeite ruhig, aber zielstrebig.",
            ),
        ),
        MateLesson(
            "kp", "Bauer zur Dame – und Matt", "Mittel", "4k3/8/4K3/4P3/8/8/8/8 w - - 0 1",
            "Dein König steht vor dem Bauern – diese Stellung ist gewonnen. Führe den Bauern zur Dame und setze matt.",
            listOf(
                "Der König geht vor dem Bauern her und erkämpft die Felder, über die der Bauer laufen soll.",
                "Opposition: Stehen sich die Könige mit einem Feld Abstand gegenüber, muss der andere ausweichen.",
                "Zieh den Bauern erst, wenn sein Weg sicher ist.",
                "Nach der Umwandlung: Matt mit der Dame wie in der ersten Lektion.",
            ),
        ),
    )
}
