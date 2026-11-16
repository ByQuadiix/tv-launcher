# TV Launcher

Werbefreier Launcher für den Xiaomi TV S Mini LED 2025 (Google TV, Android 14) mit Jellyfin auf der Startseite.

## Funktionen
Design nach der tvOS-Spezifikation (eigene Umsetzung, keine Apple-Grafiken oder -Schriften):
- **Aufbau:** obere ~60 % frei für den Hintergrund, unten nur Dock und Apps. Die ausgewählte Reihe steht
  immer auf derselben Höhe, Reihen darüber blenden aus – höchstens 1–2 Reihen sichtbar.
- **Top Shelf:** Nur wenn Jellyfin im Dock ausgewählt ist, erscheinen oben Logo, Infos und eine Reihe
  „Weiterschauen & Neu“ (Weiterschauen, nächste Folgen, neu hinzugefügt). Ein Klick öffnet den Titel in der Jellyfin-App.
- **Ambient-Hintergrund:** bei anderen Apps eine stark weichgezeichnete, entsättigte Fassung der ausgewählten Kachel.
- **Milchglas-Dock** mit den Favoriten (OK lang drücken → hinzufügen, verschieben, App-Info, Deinstallieren).
- **Fokus ohne Rahmen:** gefederte Vergrößerung (1,07×), weicher Schatten, feiner Lichtrand; nicht ausgewählte
  Banner leicht abgedunkelt; App-Namen nur unter der ausgewählten Kachel (60 %).
- **Statusleiste:** nur eine dezente Uhr. Einstellungen über die Kachel am Ende der Apps oder „OK lang drücken“.
- **Zoom beim App-Start** aus der Kachel heraus; Bilder der Shelf werden vorab geladen.
- Anmeldung bei Jellyfin per **Quick Connect** oder Benutzername/Passwort.

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
| Apple-TV-Design optimiert | ~9 % | ~23 ms (GPU ~15 ms) |
| tvOS-Spezifikation (aktuell) | **~3 %** | GPU ~20 ms |

Wichtig für künftige Änderungen (gemessen auf dem MediaTek MT5896):
- Die GPU schafft bei 1080p nur ~2 Vollbild-Schichten pro Bild. Keine zusätzlichen bildschirmfüllenden
  Hintergründe/Overlays übereinanderlegen.
- **Keine großen Compose-Verläufe (`Brush.*Gradient`)** – die sind hier teurer als ein Foto (Dithering pro Pixel).
  Stattdessen die vorgerechneten Bitmaps aus `ui/Gradients.kt` verwenden.
- Flächen unter Bildern weglassen (`FocusCard(background = Color.Transparent)`), Rahmen nur bei Fokus.
- Fotos werden über Coil als RGB_565 geladen (`LauncherApp.kt`), das halbiert die Speicherbandbreite.
- Nach jeder Installation schaltet Xiaomi die Bedienungshilfe ab – `build.ps1` schaltet sie wieder ein.
- **Keine Echtzeit-Unschärfe (RenderEffect).** Milchglas und Ambient nutzen winzige, vorab weichgezeichnete
  Bitmaps (`Images.soften`), Farbton fest eingerechnet (`Images.tone`) statt Farbfilter pro Bild.
- Hintergrund-Überblendung: altes Bild deckend liegen lassen, nur das neue mit Bild-Alpha darüber
  (keine `Crossfade`-Ebenen), und erst nach ~0,3 s Verweilen wechseln.
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
