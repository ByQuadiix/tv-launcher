# TV Launcher

Werbefreier Launcher für den Xiaomi TV S Mini LED 2025 (Google TV, Android 14) mit Jellyfin auf der Startseite.

## Funktionen
Design angelehnt an Apple TV (eigene Umsetzung, keine Apple-Grafiken):
- Großer Bereich oben mit Filmbild, Titel-Logo, Infozeile (Jahr · Freigabe · Dauer · Bewertung) und Beschreibung
  des ausgewählten Titels; Uhr, Wetter und Einstellungen als Pillen oben rechts (blenden beim Scrollen aus)
- Glas-Dock mit den Favoriten: bei einer App **OK gedrückt halten** → „Zu Favoriten hinzufügen“, verschieben, App-Info, Deinstallieren
- Jellyfin-Reihen: Weiterschauen (Logo, Fortschritt, Restzeit im Bild), Nächste Folgen, Neu hinzugefügt (Poster), Mediathek
  → ein Klick öffnet den Titel direkt in der Jellyfin-App
- Einstellungen zweispaltig (großer Titel links, Liste rechts)
- Hintergrund: Bild des ausgewählten Jellyfin-Titels, Farbverlauf, eigenes Bild (URL oder Datei)
- Anmeldung bei Jellyfin per **Quick Connect** (Code am Handy bestätigen) oder Benutzername/Passwort

## Bauen und installieren
```powershell
.\build.ps1              # optimierter Release-Build, installieren, vorkompilieren, starten
.\build.ps1 -NoInstall   # nur bauen
.\build.ps1 -Debug       # Debug-Build (deutlich langsamer, nur zur Fehlersuche)
```

### Leistung (gemessen am 16.11.2026, `dumpsys gfxinfo`, Navigation per Fernbedienung)
| Stand | Ruckelnde Bilder | Median pro Bild |
|---|---|---|
| Debug-Build | 86 % | 77 ms |
| Release + R8 + Vorkompilierung | 19 % | 29 ms |
| + weniger Vollbild-Schichten (altes, schlichtes Design) | 3 % | 13 ms |
| Apple-TV-Design, erste Version | 15 % | 34 ms |
| Apple-TV-Design optimiert (aktuell) | **~9 %** | **~23 ms** (GPU ~15 ms) |

Wichtig für künftige Änderungen (gemessen auf dem MediaTek MT5896):
- Die GPU schafft bei 1080p nur ~2 Vollbild-Schichten pro Bild. Keine zusätzlichen bildschirmfüllenden
  Hintergründe/Overlays übereinanderlegen.
- **Keine großen Compose-Verläufe (`Brush.*Gradient`)** – die sind hier teurer als ein Foto (Dithering pro Pixel).
  Stattdessen die vorgerechneten Bitmaps aus `ui/Gradients.kt` verwenden.
- Flächen unter Bildern weglassen (`FocusCard(background = Color.Transparent)`), Rahmen nur bei Fokus.
- Fotos werden über Coil als RGB_565 geladen (`LauncherApp.kt`), das halbiert die Speicherbandbreite.
- Nach jeder Installation schaltet Xiaomi die Bedienungshilfe ab – `build.ps1` schaltet sie wieder ein.
Hinweis: `build.ps1` setzt JDK 21 und einen eigenen Temp-Pfad für Java, weil der Benutzer-Temp-Ordner
ein Leerzeichen enthält und Gradle sonst mit „Unable to establish loopback connection“ abbricht.

## Home-Taste auf den neuen Launcher legen
Google TV lässt die Home-Taste immer die Google-Startseite öffnen. Zwei Möglichkeiten:

**A) Google-TV-Startseite deaktivieren**

⚠️ Getestet am 16.11.2026: Nur `launcherx` zu deaktivieren reicht auf diesem Fernseher NICHT. Dann springt
`com.google.android.tungsten.setupwraith/.RecoveryActivity` ein und der Home-Bildschirm hängt in einer
Schleife. Zusätzlich müsste `setupwraith` deaktiviert werden. Sicherer ist Variante B.
```
adb connect 192.168.2.220:5555
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
adb shell cmd package set-home-activity net.quadiix.tvlauncher/.MainActivity
```
Rückgängig machen (Google-Startseite wieder an):
```
adb shell pm enable com.google.android.apps.tv.launcherx
```

**B) Bedienungshilfe (so eingerichtet, funktioniert)** – Einstellungen → System → Bedienungshilfen →
„TV Launcher Home-Taste“ einschalten, oder per ADB:
```
adb shell settings put secure enabled_accessibility_services net.quadiix.tvlauncher/net.quadiix.tvlauncher.HomeKeyService
adb shell settings put secure accessibility_enabled 1
```
Dann fängt der Launcher die Home-Taste ab, die Google-Startseite bleibt installiert.
Hinweis: Eine per `adb shell input keyevent HOME` simulierte Taste geht am Dienst vorbei; nur die echte
Fernbedienung wird abgefangen.

## Projektaufbau
| Datei | Inhalt |
|---|---|
| `MainActivity.kt` | Launcher-Activity, Zurück-/Home-Taste, App-Änderungen |
| `LauncherViewModel.kt` | Zustand, Favoriten, Jellyfin-/Wetter-Abruf |
| `JellyfinClient.kt` | Jellyfin-REST-API (Login, Quick Connect, Resume, NextUp, Latest, Views) |
| `WeatherClient.kt` | Open-Meteo Geocoding + Wetter |
| `AppRepository.kt` | installierte Apps mit TV-Bannern |
| `HomeKeyService.kt` | Bedienungshilfe für die Home-Taste |
| `ui/HomeScreen.kt` | Startseite, Kacheln, App-Menü |
| `ui/SettingsScreen.kt` | Einstellungen |
| `ui/Components.kt` | Fernbedienungs-Bedienelemente (Fokus, OK lang drücken) |
