# ♞ Schach für Android

Schach-App mit drei Spielarten:

- **Gegen den Computer** – 10 Stufen von *Anfänger* (macht absichtlich Fehler) bis *Großmeister*
  (rechnet bis zu 10 Sekunden). Mit **Zug zurück**, **Tipp** (zeigt einen guten Zug) und **Brett drehen**.
  Die laufende Partie wird gespeichert und beim nächsten Öffnen fortgesetzt.
- **Zu zweit an einem Gerät** – abwechselnd ziehen.
- **Online gegen echte Spieler** über [lichess.org](https://lichess.org) (kostenlos, werbefrei):
  zufälligen Gegner mit ähnlicher Spielstärke suchen, einen Freund per Benutzername herausfordern,
  Herausforderungen annehmen, gewertet oder ungewertet, mit Schachuhr, Remis-Angebot und Aufgeben.

- **Schachtrainer**
  - **Eröffnungen** (12): Italienisch, Spanisch, Schottisch, Damengambit, Londoner System, Englisch
    (als Weiß) sowie Sizilianisch, Französisch, Caro-Kann, Skandinavisch, Königsindisch und
    „Schäfermatt abwehren“ (als Schwarz). Erst **ansehen** – jeder Zug mit Erklärung – dann **üben**:
    du spielst deine Seite, der Computer die andere; nach zwei Fehlversuchen wird der richtige Zug gezeigt.
  - **Mattaufgaben** (12): typische Mattbilder mit Turm, Dame, Läufer, Springer und Bauer
    (Grundreihenmatt, ersticktes Matt, arabisches Matt, Bodens Matt, Treppenmatt, Philidors Matt …),
    Matt in 1 und Matt in 2. Jeder mattsetzende Zug wird akzeptiert, im Matt in 2 verteidigt der Computer.
  - **Mattführung** (6): König + Dame, zwei Türme, Turm, zwei Läufer, Läufer + Springer und
    Bauer zur Dame – gegen den verteidigenden Computer, mit Schritt-für-Schritt-Anleitung, Tipp und Zug zurück.
  - Geschaffte Übungen bekommen einen grünen Haken.

Alle Regeln sind umgesetzt: Rochade, en passant, Umwandlung (Auswahl der Figur), Schach/Matt/Patt,
50-Züge-Regel, dreifache Stellungswiederholung und ungenügendes Material.

## Installation

Wie beim MP3-Player: GitHub baut die App bei jedem Push automatisch.
**Actions** → neuester Lauf „APK bauen“ → unter *Artifacts* **Schach-APK** herunterladen →
ZIP entpacken → `Schach-release.apk` aufs Handy → antippen → installieren.
Bei einem Release liegt `Schach-release.apk` direkt unter **Releases**.

## Online spielen – einmalige Einrichtung

1. Kostenloses Konto auf lichess.org anlegen (geht auch direkt aus der App).
2. In der App *Online gegen echte Spieler* → **Schlüssel bei Lichess erstellen** antippen.
   Die nötigen Rechte sind schon ausgewählt – unten auf „Erstellen“ tippen und den Schlüssel (`lip_…`) kopieren.
3. Schlüssel in der App einfügen → **Verbinden**.

Hinweise:
- Über die App laufen Partien mit **10 Minuten Bedenkzeit oder mehr** (Lichess erlaubt Fremd-Apps kein Blitz/Bullet bei der Gegnersuche).
- Freunde können auch mit der offiziellen Lichess-App oder im Browser gegen dich spielen.
- Der Schlüssel bleibt nur auf dem Handy und lässt sich auf lichess.org jederzeit wieder löschen.

## Technik

- Kotlin, Jetpack Compose (Material 3), keine weiteren Bibliotheken
- Eigene Schach-Engine (`engine/`): Zuggenerator (per Perft geprüft), Alpha-Beta-Suche mit
  iterativer Vertiefung, Transpositionstabelle, Ruhesuche, Nullzug- und Late-Move-Reduktion,
  Bewertung mit Figurentabellen (Mittel-/Endspiel)
- Online: Lichess Board-API (`online/LichessClient.kt`), NDJSON-Streams über `HttpURLConnection`

```
chess/src/main/java/de/schach/
├── engine/   Board (Regeln, Züge, FEN, SAN), Engine (Suche + Spielstufen), Eval
├── training/ Eröffnungen, Mattaufgaben, Mattführungen (Daten) + Prüfung „erzwingt Matt?“
├── online/   LichessClient, OnlineViewModel
└── ui/       Startmenü, Stufenauswahl, Brett, Partie- und Online-Bildschirme
```

Tests der Engine: `./gradlew :chess:testReleaseUnitTest`
