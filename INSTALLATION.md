# 📱 MP3 Player installieren – Schritt für Schritt

Diese Anleitung funktioniert auf jedem Android-Handy ab Android 8. Die Menünamen können je nach
Hersteller (Samsung, Xiaomi, Google Pixel …) leicht abweichen.

## 1. Musik aufs Handy bringen (falls noch nicht da)

1. Handy per USB-Kabel mit dem PC verbinden.
2. Auf dem Handy die Benachrichtigung „USB …“ antippen → **Dateiübertragung** wählen.
3. Am PC das Handy im Explorer öffnen und die MP3s in den Ordner **Music** kopieren
   (Unterordner pro Künstler oder Album sind kein Problem).

## 2. APK herunterladen

1. Auf dem Handy im Browser (z. B. Chrome) diesen Link öffnen:
   **https://github.com/hendrikel80-ai/MP3-Player/releases/latest**
2. Unter **Assets** auf **MP3-Player-release.apk** tippen.
3. Falls Chrome warnt „Datei kann schädlich sein“ → **Trotzdem herunterladen**.

> Alternativ: Jemand schickt dir die APK-Datei per WhatsApp, Signal, E-Mail oder Google Drive.

## 3. Installieren

1. Nach dem Download auf **Öffnen** tippen (oder die Datei in der App **Dateien** / **Downloads** antippen).
2. Beim ersten Mal erscheint: *„Dein Smartphone darf aus Sicherheitsgründen keine unbekannten Apps
   aus dieser Quelle installieren“* → **Einstellungen** → Schalter **„Dieser Quelle vertrauen“** /
   **„Apps aus dieser Quelle zulassen“** einschalten → zurück.
3. **Installieren** antippen.
4. Falls **Google Play Protect** warnt („Unbekannte App“) → **Weitere Details** → **Trotzdem installieren**.
   (Die App ist nur nicht im Play Store – das ist normal bei selbst gebauten Apps.)
5. **Öffnen** antippen.

## 4. Erster Start

1. Frage *„Zugriff auf Musik und Audio erlauben?“* → **Zulassen**.
2. Frage *„Benachrichtigungen erlauben?“* → **Zulassen** (damit die Steuerung in der
   Benachrichtigung und auf dem Sperrbildschirm erscheint).
3. Die App liest jetzt deine Musik ein – fertig! 🎵

## Update auf eine neue Version

- **Normalfall:** Neue APK wie oben herunterladen und installieren → „Aktualisieren“. Playlists bleiben erhalten.
- **Meldung „App nicht installiert“ / „Paket steht in Konflikt“:** Die neue Version ist mit einem
  anderen Schlüssel signiert. Dann alte App deinstallieren (lange auf das Symbol drücken →
  **Deinstallieren**) und neu installieren. ⚠️ Dabei gehen die eigenen Playlists verloren.

## Probleme?

| Problem | Lösung |
|---|---|
| „Keine Musik gefunden“ | Prüfen, ob die MP3s im Ordner *Music* liegen; dann ⋮ → **Musik neu einlesen**. |
| Zugriff versehentlich abgelehnt | In der App **App-Einstellungen öffnen** → Berechtigungen → **Musik und Audio** → Zulassen. |
| Keine Steuerung auf dem Sperrbildschirm | Einstellungen → Apps → MP3 Player → Benachrichtigungen einschalten. |
| Musik stoppt im Hintergrund (v. a. Xiaomi/Huawei) | Einstellungen → Apps → MP3 Player → Akku → **Nicht einschränken**. |
| Genre fehlt / falscher Künstler | Die Infos kommen aus den MP3-Tags. Am PC z. B. mit *Mp3tag* korrigieren. |
