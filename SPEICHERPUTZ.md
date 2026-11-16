# 🧹 Speicherputz – Handyspeicher sicher aufräumen

Speicherputz findet **unsichtbare und überflüssige Dateien** auf deinem Android-Handy, zeigt sie dir
übersichtlich an – und löscht **nur das, was du selbst auswählst und bestätigst**.

## Was die App findet

| Kategorie | Was ist das? | Einstufung |
|---|---|---|
| **Papierkorb** | Fotos/Videos, die du schon gelöscht hast, die aber noch bis zu 30 Tage Platz belegen | 🟢 Unbedenklich |
| **Vorschaubilder-Cache** | Unsichtbare Ordner `.thumbnails` der Galerie – werden bei Bedarf neu erstellt | 🟢 Unbedenklich |
| **Temporäre Dateien & Protokolle** | abgebrochene Downloads, `.tmp`, `.log`, Absturzberichte, `Thumbs.db`, `.DS_Store` (nur älter als 1 Tag) | 🟢 Unbedenklich |
| **Leere Ordner** | Ordner ohne eine einzige Datei | 🟢 Unbedenklich |
| **Installationsdateien (APK)** | heruntergeladene App-Installer – die App zeigt an, ob die App schon installiert ist | 🟢 Unbedenklich |
| **Messenger-Kopien & alte Sicherungen** | WhatsApp: Kopien selbst verschickter Medien, angesehene Status, ältere tägliche Chat-Sicherungen · Telegram: heruntergeladene Medien | 🟡 Bitte prüfen |
| **Alte Downloads** | Dateien im Download-Ordner, älter als 90 Tage | 🟡 Bitte prüfen |
| **Große Dateien** | einzelne Dateien ab 100 MB | 🟡 Bitte prüfen |
| **Doppelte Dateien** | inhaltlich identische Dateien – eine Kopie bleibt immer erhalten | 🟡 Bitte prüfen |
| **App-Zwischenspeicher** | Cache aller Apps, sortiert nach Größe – mit Direktsprung zu „Cache leeren“ | 🟢 Unbedenklich |

## Damit das Handy danach weiter problemlos läuft

- **Nichts wird automatisch gelöscht.** Am Anfang ist nichts ausgewählt; vor dem Löschen kommt immer eine Rückfrage.
- **Systemdateien und App-Daten sind tabu:** `Android/data`, `Android/obb` und Systembereiche werden gar nicht erst durchsucht.
- **Standardordner** (DCIM, Download, Music, Pictures …) werden nie als „leer“ gelöscht, `.nomedia`-Dateien nie angefasst
  (sonst würden versteckte Bilder plötzlich in der Galerie auftauchen).
- **Duplikate:** Das „Original“ (z. B. das Kamera-Foto) wird markiert; alle Kopien einer Gruppe zu löschen ist gesperrt.
- **Aktuelle WhatsApp-Sicherung** (`msgstore.db.crypt…`) bleibt immer – nur ältere, datierte Sicherungen werden vorgeschlagen.
- **Kurz vor dem Löschen** wird jede Datei nochmal geprüft: Hat sie sich seit der Suche verändert (z. B. weil eine App
  sie gerade benutzt), wird sie übersprungen.
- **Vor dem Löschen ansehen:** Fotos und Videos haben Vorschaubilder; mit 👁 öffnest du jede Datei in einer passenden App.

> ⚠️ Gelöschte Dateien sind **endgültig weg** (sie landen nicht im Papierkorb). Lösche persönliche Dateien
> (🟡-Kategorien) nur, wenn du sie nicht mehr brauchst oder sie auf PC/Cloud gesichert hast.

## Installation

1. Auf GitHub im Repo **Actions** → neuester Lauf „APK bauen“ → unten unter *Artifacts* **Speicherputz-APK**
   herunterladen (ZIP entpacken → `Speicherputz-release.apk`).
   *Oder*, falls veröffentlicht, unter **Releases** die Version „Speicherputz …“ öffnen und die APK laden.
2. Die APK aufs Handy bringen und antippen → „Installation aus unbekannten Quellen erlauben“ → **Installieren**.
   Warnt Google Play Protect: **Weitere Details → Trotzdem installieren** (die App ist nur nicht im Play Store).

## Erster Start

1. **Zugriff erlauben** antippen → Android öffnet „Zugriff auf alle Dateien“ → Schalter bei **Speicherputz** einschalten
   → zurück. (Android 8–10: einfach „Zulassen“.)
2. **Speicher analysieren** antippen. Je nach Datenmenge dauert das einige Sekunden bis wenige Minuten.
3. Kategorie antippen → Dateien ansehen → auswählen (oder „Alle auswählen“) → unten **Löschen …** → bestätigen.

### App-Caches leeren

Android erlaubt keiner App, den Cache **anderer** Apps zu löschen. Speicherputz zeigt dir aber, welche Apps
viel Cache haben (z. B. Instagram, TikTok, Chrome – oft mehrere GB):

1. Startseite → **App-Zwischenspeicher (Cache)** → **Zugriff erlauben** → „Speicherputz“ → Schalter ein → zurück.
2. Bei einer App **Cache leeren** antippen → **Speicher** → **Cache leeren** → zurück.

> ⚠️ Dort nur **„Cache leeren“** – **nicht** „Speicher löschen“ / „Daten löschen“, sonst wirst du in der App
> abgemeldet und Chats oder Einstellungen können verloren gehen.

## Weitere Tipps gegen vollen Speicher

- **Google Fotos:** „Speicherplatz freigeben“ entfernt Fotos vom Handy, die schon in der Cloud gesichert sind.
- **WhatsApp:** Einstellungen → Speicher und Daten → **Speicher verwalten** zeigt große Dateien je Chat.
- **Ungenutzte Apps deinstallieren:** Einstellungen → Apps (sortieren nach Größe).

## Technik

Eigenes Modul `cleaner/` in diesem Repo (Kotlin, Jetpack Compose, Android 8+). Such- und Löschregeln liegen in
`cleaner/src/main/java/de/speicherputz/scan/` und sind mit Unit-Tests abgesichert (`./gradlew :cleaner:testReleaseUnitTest`).
