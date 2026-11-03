# 🎵 MP3 Player für Android

Ein einfacher, werbefreier Musik-Player für die MP3s auf deinem Android-Handy.

## Funktionen

- **Automatische Playlists aus den Metadaten** (ID3-Tags): Titel, **Künstler**, **Alben** und **Genres**
  werden automatisch erkannt und sortiert.
- **Playlist aus Metadaten erstellen**: Im Tab *Playlists* → „Aus Metadaten“ einen oder mehrere
  Künstler, Alben oder Genres auswählen (z. B. „Rock + Pop“) und als eigene Playlist speichern.
  Auf jeder Künstler-/Album-/Genre-Seite gibt es außerdem „Als Playlist speichern“.
- **Eigene Playlists**: erstellen, umbenennen, löschen, Titel hinzufügen (⋮-Menü bei jedem Song),
  Reihenfolge ändern und Titel entfernen.
- Wiedergabe im **Hintergrund** mit Steuerung in der Benachrichtigung, auf dem Sperrbildschirm,
  per Kopfhörer, Bluetooth und im Auto.
- Zufallswiedergabe, Wiederholen (alle / einen), Warteschlange („Als Nächstes abspielen“), Suche.
- Album-Cover aus den MP3s, helles/dunkles Design passend zum Handy.
- Neue MP3s werden automatisch erkannt.

## Installation auf dem Handy (für dich und deine Frau)

Die App wird bei jedem Push automatisch von GitHub gebaut – du brauchst kein Android Studio.

1. Auf GitHub im Repo auf **Actions** → den neuesten Lauf „APK bauen“ öffnen →
   unten unter *Artifacts* **MP3-Player-APK** herunterladen (ZIP entpacken → `MP3-Player-release.apk`).
   *Oder* bei einer veröffentlichten Version unter **Releases** direkt die APK laden (siehe unten).
2. Die APK aufs Handy bringen (z. B. per WhatsApp an dich selbst, Google Drive, USB-Kabel).
3. Auf dem Handy antippen → Android fragt einmalig nach „Installation aus unbekannten Quellen
   erlauben“ → erlauben → **Installieren**.
4. App öffnen und den Zugriff auf Musik erlauben.

**Für weitere Personen** (z. B. deine Frau): einfach dieselbe APK-Datei weitergeben und wie oben
installieren. Jedes Handy hat seine eigene Musik und seine eigenen Playlists.

**Updates**: Neue APK genauso installieren – sie wird über die alte Version installiert,
Playlists bleiben erhalten.

### Feste Download-Version (Release) erstellen

Für einen dauerhaften Download-Link ein Tag pushen:

```bash
git tag v1.0.0
git push origin v1.0.0
```

Dann erscheint die APK unter **Releases**. (Ist das Repo privat, braucht man zum Herunterladen
einen GitHub-Zugang – dann die APK einfach selbst weiterschicken.)

## Technik

- Kotlin, Jetpack Compose (Material 3)
- AndroidX Media3 / ExoPlayer mit `MediaSessionService` für Hintergrund-Wiedergabe
- Musikbibliothek über Android `MediaStore` (Titel, Künstler, Album, Genre, Tracknummer, Jahr, Cover)
- Playlists als JSON im privaten App-Speicher
- Mindestversion Android 8.0 (API 26)

Projektaufbau:

```
app/src/main/java/de/mp3player/
├── MainActivity.kt
├── data/          Song/Library (Gruppierung), MusicRepository (MediaStore), PlaylistStore
├── playback/      PlaybackService (Hintergrund-Player), PlayerConnection
└── ui/            Bildschirme: Home (Tabs), Detailseiten, Player, Dialoge
```

Lokal bauen (mit Android SDK): `./gradlew assembleRelease`

## Einmalig einrichten: eigener Signaturschlüssel (empfohlen)

Android installiert ein Update nur, wenn es mit **demselben Schlüssel** signiert ist wie die
installierte Version. Ohne eigenen Schlüssel signiert GitHub jede APK mit einem wechselnden
Test-Schlüssel – dann muss man die App vor einem Update deinstallieren (Playlists gehen verloren).

So richtest du einen festen Schlüssel ein (einmalig, z. B. auf einem PC mit Java):

```bash
keytool -genkeypair -keystore release.keystore -alias mp3player -keyalg RSA -keysize 2048 -validity 36500
base64 -w0 release.keystore > keystore.txt      # macOS: base64 -i release.keystore > keystore.txt
```

Dann auf GitHub: **Settings → Secrets and variables → Actions → New repository secret**:

| Name                | Wert                                   |
|---------------------|----------------------------------------|
| `KEYSTORE_BASE64`   | Inhalt von `keystore.txt`              |
| `KEYSTORE_PASSWORD` | das beim Erstellen gewählte Passwort   |
| `KEY_ALIAS`         | `mp3player`                            |
| `KEY_PASSWORD`      | dasselbe Passwort                      |

Die `release.keystore` gut aufheben und **nie** ins Repo committen.
