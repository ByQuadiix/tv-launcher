package net.quadiix.tvlauncher.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.quadiix.tvlauncher.LauncherViewModel
import net.quadiix.tvlauncher.startSafely

@Composable
fun SettingsScreen(vm: LauncherViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val loggedIn by vm.jfLoggedIn.collectAsState()
    val user by vm.jfUser.collectAsState()
    val server by vm.jfServer.collectAsState()
    val jfMessage by vm.jfMessage.collectAsState()
    val qcCode by vm.quickConnectCode.collectAsState()
    val weatherEnabled by vm.weatherEnabled.collectAsState()
    val city by vm.weatherCity.collectAsState()
    val mode by vm.wallpaperMode.collectAsState()
    val wallUrl by vm.wallpaperUrl.collectAsState()
    var weatherMsg by remember { mutableStateOf<String?>(null) }

    val first = remember { FocusRequester() }
    val full = Modifier.fillMaxWidth()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            vm.setWallpaperCustom(uri.toString())
        }
    }

    // Fokus neu setzen, wenn sich der Anmeldestatus ändert (Knöpfe tauschen dann)
    LaunchedEffect(loggedIn, qcCode != null) {
        delay(60)
        runCatching { first.requestFocus() }
    }

    // Kein eigener Vollbild-Hintergrund: das Filmbild dahinter wird für die Einstellungen stark abgedunkelt.
    Row(Modifier.fillMaxSize()) {
        // Links: großer Titel wie bei den Apple-TV-Einstellungen
        Column(
            Modifier.width(360.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("⚙", fontSize = 110.sp, color = Color.White.copy(alpha = 0.85f))
            Spacer(Modifier.height(8.dp))
            Text("Einstellungen", fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text("TV Launcher 1.0", fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f))
        }
        // Rechts: die Liste
        Column(
            Modifier
                .fillMaxHeight()
                .width(560.dp)
                .verticalScroll(rememberScrollState())
                .padding(top = 44.dp, bottom = 44.dp, end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ---------------- Jellyfin
            Header("Jellyfin", first = true)
            if (loggedIn) {
                Info("Angemeldet als ${user ?: "?"} · $server")
                TvButton("Inhalte jetzt aktualisieren", full.focusRequester(first)) { vm.refreshJellyfin() }
                TvButton("Abmelden", full, value = user) { vm.logout() }
            } else {
                TvButton("Server", full.focusRequester(first), value = server) {
                    showTextPrompt(ctx, "Adresse des Jellyfin-Servers", server) { vm.setServer(it) }
                }
                val code = qcCode
                if (code != null) {
                    Column(
                        full.background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp)).padding(18.dp)
                    ) {
                        Text("Quick-Connect-Code", fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f))
                        Text(code, fontSize = 42.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp, color = Color.White)
                        Text(
                            "Öffne Jellyfin am Handy oder PC → Benutzer-Menü → Einstellungen → Quick Connect " +
                                "und gib diesen Code ein. Der Fernseher meldet sich dann automatisch an.",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                    }
                    TvButton("Quick Connect abbrechen", full) { vm.cancelQuickConnect() }
                } else {
                    TvButton("Mit Quick Connect anmelden", full) { vm.startQuickConnect() }
                }
                TvButton("Mit Benutzername und Passwort anmelden", full) {
                    showTextPrompt(ctx, "Jellyfin-Benutzername") { u ->
                        if (u.isNotBlank()) {
                            showTextPrompt(ctx, "Passwort für $u", password = true) { p ->
                                vm.loginWithPassword(u, p)
                            }
                        }
                    }
                }
            }
            jfMessage?.let { Info(it) }

            // ---------------- Wetter
            Header("Wetter")
            TvButton("Wetter anzeigen", full, value = if (weatherEnabled) "An" else "Aus") {
                vm.setWeatherEnabled(!weatherEnabled)
            }
            TvButton("Ort", full, value = city ?: "nicht festgelegt") {
                showTextPrompt(ctx, "Ort für das Wetter (z. B. München)", city ?: "") { name ->
                    scope.launch {
                        weatherMsg = "Suche …"
                        weatherMsg = vm.setWeatherCity(name) ?: "Ort gespeichert"
                    }
                }
            }
            weatherMsg?.let { Info(it) }

            // ---------------- Hintergrund
            Header("Hintergrund")
            TvButton("Bild des ausgewählten Titels", full, selected = mode == "jellyfin") {
                vm.setWallpaperMode("jellyfin")
            }
            TvButton("Farbverlauf", full, selected = mode == "gradient") { vm.setWallpaperMode("gradient") }
            val isUrl = wallUrl?.startsWith("http") == true
            TvButton("Eigenes Bild aus dem Netz (URL)", full, selected = mode == "custom" && isUrl) {
                showTextPrompt(ctx, "Adresse des Bildes (URL)", if (isUrl) wallUrl!! else "https://") {
                    if (it.trim().startsWith("http")) vm.setWallpaperCustom(it.trim())
                }
            }
            TvButton("Eigenes Bild vom Gerät", full, selected = mode == "custom" && !isUrl && wallUrl != null) {
                try {
                    picker.launch(arrayOf("image/*"))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(
                        ctx,
                        "Dieser Fernseher hat keine Dateiauswahl – nutze stattdessen eine Bild-URL",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }

            // ---------------- System
            Header("System")
            Info("Die Home-Taste öffnet diesen Launcher über die Bedienungshilfe „TV Launcher Home-Taste“.")
            TvButton("Bedienungshilfen", full) {
                startSafely(ctx, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            TvButton("Android-Einstellungen", full) {
                startSafely(ctx, Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            TvButton("Zurück zur Startseite", full) { vm.closeSettings() }
        }
    }
}

@Composable
private fun Header(text: String, first: Boolean = false) {
    Text(
        text.uppercase(),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        color = Color.White.copy(alpha = 0.55f),
        modifier = Modifier.padding(start = 6.dp, top = if (first) 0.dp else 22.dp, bottom = 2.dp),
    )
}

@Composable
private fun Info(text: String) {
    Text(text, fontSize = 13.sp, color = Color.White.copy(alpha = 0.65f), modifier = Modifier.padding(horizontal = 6.dp))
}
