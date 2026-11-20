# TV Launcher

Werbefreier Launcher für den Xiaomi TV S Mini LED 2025 (Google TV, Android 14) mit Jellyfin auf der Startseite.

## Funktionen
Design im Apple-TV-/tvOS-Stil (eigene Umsetzung, optimiert für 55" QD-Mini LED & MT5896):
- **Zweistufiger Aufbau:** Im Dock bleiben die oberen ~60 % frei für Hintergrund bzw. Top Shelf. Beim
  Runterscrollen in „Apps“ gleitet das Dock nach oben weg und gibt den ganzen Bildschirm für das
  symmetrische 5-Spalten-App-Raster (3+ Reihen gleichzeitig) frei.
- **Jellyfin Top Shelf mit Kategorien:** Nur wenn Jellyfin im Dock fokussiert ist, erscheinen oben
  Logo, Infos und die getrennten Kategorien **Weitergucken**, **Als Nächstes** und **Neu**. Ein Klick
  öffnet den Titel direkt in der Jellyfin-App; „OK lang drücken“ bietet u. a. „Als gesehen markieren“.
- **Ambient-Hintergrund:** bei anderen Apps eine stark weichgezeichnete, entsättigte Fassung der ausgewählten Kachel.
- **Milchglas-Dock & Live-Sortierung:** Favoriten lassen sich über das rechte Seitenmenü („OK lang drücken“)
  live mit ◀ ▶ im Dock verschieben; ungenutzte Apps können ausgeblendet werden.
- **10-Foot-Fokus & Lesbarkeit:** gefederte Vergrößerung (1,08×), weicher Schatten, klar erkennbarer
  Lichtrand; getönte Kacheln für Apps ohne TV-Banner; gut lesbare Typografie aus 3 m Entfernung.
- **Dezente obere Leiste:** Uhrzeit + schneller Zugriff auf die 2-Spalten-Einstellungen (und optional HDMI-Eingänge).
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
| `LauncherViewModel.kt` | Zustand, Favoriten, ausgeblendete Apps, Jellyfin-Kategorien, HDMI-Eingänge |
| `JellyfinClient.kt` | Jellyfin-REST-API (Login, Quick Connect, Resume, NextUp, Latest, MarkPlayed) |
| `AppRepository.kt` | installierte Apps mit TV-Bannern und abgeleiteter Kartenfarbe |
| `TvInputs.kt` | HDMI-/Hardware-Eingänge über den Android-TV-Input-Dienst |
| `HomeKeyService.kt` | Bedienungshilfe für die Home-Taste |
| `ui/HomeScreen.kt` | Startseite, Kategorie-Shelf, Milchglas-Dock, App-Raster, Seitenmenüs |
| `ui/SettingsScreen.kt` | 2-Spalten-Einstellungen |
| `ui/Components.kt` | Fernbedienungs-Bedienelemente (Fokus, OK lang drücken, Schalter, Kategorie-Pills) |
| `ui/Icons.kt` | schlanke Vektor-Icons für die TV-Oberfläche |
