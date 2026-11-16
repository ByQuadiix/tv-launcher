# Baut den TV Launcher (optimierter Release-Build) und installiert ihn auf dem Fernseher.
#   .\build.ps1            -> bauen + installieren + vorkompilieren + starten
#   .\build.ps1 -NoInstall -> nur bauen
#   .\build.ps1 -Debug     -> Debug-Build (langsamer, nur zum Fehlersuchen)
param(
    [string]$TvIp = "192.168.2.220",
    [switch]$NoInstall,
    [switch]$Debug
)
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
# Der Temp-Ordner des Benutzers enthält ein Leerzeichen; daran scheitern Javas lokale Sockets.
$env:JAVA_TOOL_OPTIONS = "-Djdk.net.unixdomain.tmpdir=C:\Windows\Temp"

$variant = if ($Debug) { "debug" } else { "release" }
$task = if ($Debug) { "assembleDebug" } else { "assembleRelease" }

& .\gradlew.bat $task --console=plain
if ($LASTEXITCODE -ne 0) { throw "Build fehlgeschlagen" }
if ($NoInstall) { return }

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$dev = "${TvIp}:5555"
$pkg = "net.quadiix.tvlauncher"
& $adb connect $dev | Out-Null
& $adb -s $dev install -r "app\build\outputs\apk\$variant\app-$variant.apk"
if ($LASTEXITCODE -ne 0) {
    # Wechsel zwischen Debug und Release kann an der "debuggable"-Markierung scheitern -> neu installieren.
    # Achtung: Dabei gehen die Einstellungen (Favoriten, Jellyfin-Anmeldung) verloren.
    throw "Installation fehlgeschlagen"
}
& $adb -s $dev shell am start -n "$pkg/.MainActivity"
if (-not $Debug) {
    # Vorkompilieren, damit nichts mehr interpretiert wird – der wichtigste Punkt gegen Ruckler.
    # Xiaomi macht aus "speed" ein "speed-profile"; dafür muss die App vorher einmal gelaufen sein,
    # damit der ProfileInstaller das Compose-Profil ablegen kann.
    Start-Sleep -Seconds 6
    & $adb -s $dev shell cmd package compile -m speed -f $pkg
    & $adb -s $dev shell am start -n "$pkg/.MainActivity"
}
# Installieren/Kompilieren beendet die App; Xiaomi schaltet dabei die Bedienungshilfe für die
# Home-Taste ab. Deshalb hier immer wieder einschalten (ohne andere Dienste zu überschreiben).
$svc = "$pkg/$pkg.HomeKeyService"
$cur = (& $adb -s $dev shell settings get secure enabled_accessibility_services).Trim()
if ($cur -eq "null" -or $cur -eq "") { $new = $svc }
elseif ($cur.Split(':') -contains $svc) { $new = $cur }
else { $new = "${cur}:$svc" }
& $adb -s $dev shell settings put secure enabled_accessibility_services $new
& $adb -s $dev shell settings put secure accessibility_enabled 1
