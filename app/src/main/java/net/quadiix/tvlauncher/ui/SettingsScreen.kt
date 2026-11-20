package net.quadiix.tvlauncher.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import net.quadiix.tvlauncher.LauncherViewModel
import net.quadiix.tvlauncher.NavEvent
import net.quadiix.tvlauncher.startSafely

private enum class SettingsTab(val title: String, val icon: ImageVector) {
    JELLYFIN("Jellyfin", TvIcons.Film),
    HOME("Startseite", TvIcons.Home),
    APPS("Apps", TvIcons.Grid),
    WALLPAPER("Hintergrund", TvIcons.Image),
    SYSTEM("System", TvIcons.Settings),
}

@Composable
fun SettingsScreen(vm: LauncherViewModel) {
    val ctx = LocalContext.current

    val loggedIn by vm.jfLoggedIn.collectAsState()
    val user by vm.jfUser.collectAsState()
    val server by vm.jfServer.collectAsState()
    val jfMessage by vm.jfMessage.collectAsState()
    val qcCode by vm.quickConnectCode.collectAsState()
    val mode by vm.wallpaperMode.collectAsState()
    val wallUrl by vm.wallpaperUrl.collectAsState()
    val apps by vm.apps.collectAsState()
    val hiddenApps by vm.hiddenApps.collectAsState()
    val hideFavsInAll by vm.hideFavoritesInAllApps.collectAsState()
    val showHdmiQuick by vm.showHdmiQuickAction.collectAsState()
    val tvInputs by vm.tvInputs.collectAsState()

    var activeTab by remember { mutableStateOf(SettingsTab.JELLYFIN) }
    var focusInDetail by remember { mutableStateOf(false) }

    val tabRequesters = remember { SettingsTab.entries.associateWith { FocusRequester() } }
    val detailFirst = remember { FocusRequester() }

    val versionName = remember(ctx) {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "1.2.0"
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            vm.setWallpaperCustom(uri.toString())
        }
    }

    LaunchedEffect(Unit) {
        delay(60)
        runCatching { tabRequesters[activeTab]?.requestFocus() }
    }

    // Fokus im rechten Bereich halten, wenn sich beim An-/Abmelden die Knöpfe austauschen
    LaunchedEffect(loggedIn, qcCode != null) {
        if (focusInDetail && activeTab == SettingsTab.JELLYFIN) {
            delay(60)
            runCatching { detailFirst.requestFocus() }
        }
    }

    // Zurück-Taste: erst aus der Detail-Spalte zurück in die linke Leiste, dann Einstellungen schließen
    LaunchedEffect(Unit) {
        vm.navEvents.collect { ev ->
            if (ev == NavEvent.BACK) {
                if (focusInDetail) {
                    runCatching { tabRequesters[activeTab]?.requestFocus() }
                } else {
                    vm.closeSettings()
                }
            }
        }
    }

    // Kein eigener Vollbild-Hintergrund: das stark abgedunkelte Hintergrundbild bleibt liegen (GPU-Budget).
    Row(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 49.dp, vertical = 42.dp),
        horizontalArrangement = Arrangement.spacedBy(36.dp),
    ) {
        // ---------------- Linke Navigationsleiste (Apple-TV-Split-View)
        Column(
            Modifier
                .width(268.dp)
                .fillMaxHeight()
                .onFocusChanged { if (it.hasFocus) focusInDetail = false },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 8.dp, bottom = 22.dp),
            ) {
                Icon(
                    imageVector = TvIcons.Settings,
                    contentDescription = null,
                    tint = Palette.TextPrimary,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "Einstellungen",
                        style = AiryText,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Medium,
                        color = Palette.TextPrimary,
                    )
                    Text(
                        "TV Launcher $versionName",
                        style = AiryText,
                        fontSize = 13.sp,
                        color = Palette.TextMuted,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsTab.entries.forEach { tab ->
                    val badge = if (tab == SettingsTab.APPS && hiddenApps.isNotEmpty()) "${hiddenApps.size}" else null
                    TvButton(
                        text = tab.title,
                        icon = tab.icon,
                        active = activeTab == tab,
                        value = badge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(tabRequesters.getValue(tab))
                            .focusProperties { right = detailFirst },
                        onFocused = { activeTab = tab },
                        onClick = {
                            activeTab = tab
                            runCatching { detailFirst.requestFocus() }
                        },
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            TvButton(
                text = "Zurück zur Startseite",
                icon = TvIcons.ChevronLeft,
                modifier = Modifier.fillMaxWidth(),
                onClick = { vm.closeSettings() },
            )
        }

        // ---------------- Rechte Detail-Spalte
        val backToRail = Modifier.focusProperties { left = tabRequesters.getValue(activeTab) }
        val firstMod = Modifier
            .fillMaxWidth()
            .focusRequester(detailFirst)
            .then(backToRail)
        val itemMod = Modifier
            .fillMaxWidth()
            .then(backToRail)

        val scrollState = rememberScrollState()
        LaunchedEffect(activeTab) { scrollState.scrollTo(0) }

        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .onFocusChanged { if (it.hasFocus) focusInDetail = true }
                .verticalScroll(scrollState)
                .padding(top = 6.dp, end = 6.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                activeTab.title,
                style = AiryText,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                color = Palette.TextPrimary,
                modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
            )

            when (activeTab) {
                SettingsTab.JELLYFIN -> {
                    if (loggedIn) {
                        InfoBox("Angemeldet als ${user ?: "?"} · $server")
                        TvButton("Inhalte jetzt aktualisieren", firstMod, icon = TvIcons.Refresh) {
                            vm.refreshJellyfin()
                            Toast.makeText(ctx, "Jellyfin wird aktualisiert …", Toast.LENGTH_SHORT).show()
                        }
                        TvButton("Abmelden", itemMod, value = user, destructive = true) { vm.logout() }
                    } else {
                        TvButton("Server", firstMod, value = server) {
                            showTextPrompt(ctx, "Adresse des Jellyfin-Servers", server) { vm.setServer(it) }
                        }
                        val code = qcCode
                        if (code != null) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .background(Palette.Glass, RoundedCornerShape(16.dp))
                                    .border(1.dp, Palette.GlassEdge, RoundedCornerShape(16.dp))
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text("Quick-Connect-Code", style = AiryText, fontSize = 14.sp, color = Palette.TextSecondary)
                                Text(
                                    code,
                                    fontSize = 42.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 6.sp,
                                    color = Palette.TextPrimary,
                                )
                                Text(
                                    "Öffne Jellyfin am Handy oder PC → Benutzer-Menü → Einstellungen → Quick Connect " +
                                        "und gib diesen Code ein. Der Fernseher meldet sich dann automatisch an.",
                                    style = AiryText,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    color = Palette.TextSecondary,
                                )
                            }
                            TvButton("Quick Connect abbrechen", itemMod) { vm.cancelQuickConnect() }
                        } else {
                            TvButton("Mit Quick Connect anmelden", itemMod) { vm.startQuickConnect() }
                        }
                        TvButton("Mit Benutzername und Passwort anmelden", itemMod) {
                            showTextPrompt(ctx, "Jellyfin-Benutzername") { u ->
                                if (u.isNotBlank()) {
                                    showTextPrompt(ctx, "Passwort für $u", password = true) { p ->
                                        vm.loginWithPassword(u, p)
                                    }
                                }
                            }
                        }
                    }
                    jfMessage?.let { InfoText(it) }
                    InfoText("Die Top Shelf erscheint oben auf der Startseite, sobald Jellyfin im Dock ausgewählt ist – mit den Kategorien Weitergucken, Als Nächstes und Neu.")
                }

                SettingsTab.HOME -> {
                    TvToggleButton(
                        text = "Favoriten unter „Apps“ ausblenden",
                        subtitle = "Vermeidet doppelte Kacheln im App-Raster unterhalb des Docks.",
                        checked = hideFavsInAll,
                        modifier = firstMod,
                        onToggle = { vm.setHideFavoritesInAllApps(!hideFavsInAll) },
                    )
                    TvToggleButton(
                        text = "HDMI-Schnellzugriff oben anzeigen",
                        subtitle = "Zeigt ein dezentes TV-Symbol neben den Einstellungen in der oberen Leiste.",
                        checked = showHdmiQuick,
                        modifier = itemMod,
                        onToggle = { vm.setShowHdmiQuickAction(!showHdmiQuick) },
                    )
                    InfoBox(
                        "Tipp zur Bedienung: Im Dock kannst du mit „OK lang drücken“ das Seitenmenü öffnen, " +
                            "Kacheln live mit ◀ ▶ verschieben oder unerwünschte Apps ausblenden."
                    )
                }

                SettingsTab.APPS -> {
                    val hiddenEntries = remember(apps, hiddenApps) {
                        apps.filter { it.pkg in hiddenApps }
                    }
                    TvButton(
                        text = "App-Liste neu einlesen",
                        icon = TvIcons.Refresh,
                        value = "${apps.size} Apps",
                        modifier = firstMod,
                        onClick = {
                            vm.refreshApps()
                            Toast.makeText(ctx, "App-Liste aktualisiert", Toast.LENGTH_SHORT).show()
                        },
                    )
                    SectionHeader("Ausgeblendete Apps")
                    if (hiddenEntries.isEmpty()) {
                        InfoBox("Keine ausgeblendeten Apps. Halte auf einer App „OK“ gedrückt → „App ausblenden“, um System- oder ungenutzte Apps zu verbergen.")
                    } else {
                        InfoText("Wähle eine App aus, um sie wieder auf der Startseite anzuzeigen.")
                        hiddenEntries.forEach { app ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                val thumb = app.banner ?: app.icon
                                if (thumb != null) {
                                    Image(
                                        thumb,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(if (app.banner != null) 64.dp else 36.dp, 36.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                    )
                                }
                                TvButton(
                                    text = app.label,
                                    value = "Einblenden",
                                    modifier = itemMod.weight(1f),
                                    onClick = { vm.unhideApp(app.pkg) },
                                )
                            }
                        }
                    }
                }

                SettingsTab.WALLPAPER -> {
                    TvButton(
                        "Bild des ausgewählten Titels",
                        firstMod,
                        selected = mode == "jellyfin",
                    ) { vm.setWallpaperMode("jellyfin") }
                    TvButton(
                        "Ruhiger Farbverlauf",
                        itemMod,
                        selected = mode == "gradient",
                    ) { vm.setWallpaperMode("gradient") }
                    val isUrl = wallUrl?.startsWith("http") == true
                    TvButton(
                        "Eigenes Bild aus dem Netz (URL)",
                        itemMod,
                        selected = mode == "custom" && isUrl,
                    ) {
                        showTextPrompt(ctx, "Adresse des Bildes (URL)", if (isUrl) wallUrl!! else "https://") {
                            if (it.trim().startsWith("http")) vm.setWallpaperCustom(it.trim())
                        }
                    }
                    TvButton(
                        "Eigenes Bild vom Gerät",
                        itemMod,
                        selected = mode == "custom" && !isUrl && wallUrl != null,
                    ) {
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
                    InfoText("Bei anderen Apps außerhalb von Jellyfin wird automatisch ein weicher, entsättigter Ambient-Hintergrund genutzt.")
                }

                SettingsTab.SYSTEM -> {
                    if (tvInputs.isNotEmpty()) {
                        SectionHeader("HDMI- & TV-Eingänge", first = true)
                        tvInputs.forEachIndexed { idx, input ->
                            TvButton(
                                text = input.label,
                                icon = TvIcons.TvInput,
                                modifier = if (idx == 0) firstMod else itemMod,
                                onClick = {
                                    vm.closeSettings()
                                    vm.openTvInput(ctx, input)
                                },
                            )
                        }
                    }
                    SectionHeader("Gerät & Home-Taste", first = tvInputs.isEmpty())
                    TvButton(
                        "Bedienungshilfen (Home-Taste)",
                        if (tvInputs.isEmpty()) firstMod else itemMod,
                        icon = TvIcons.Home,
                    ) {
                        startSafely(ctx, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    TvButton("Android-Einstellungen", itemMod, icon = TvIcons.Settings) {
                        startSafely(ctx, Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    InfoBox(
                        "Die Home-Taste der Xiaomi-Fernbedienung öffnet diesen Launcher über die Bedienungshilfe " +
                            "„TV Launcher Home-Taste“. Nach einem App-Update muss sie ggf. wieder aktiviert werden."
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String, first: Boolean = false) {
    Text(
        text.uppercase(),
        fontSize = 12.5.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        color = Palette.TextSecondary,
        modifier = Modifier.padding(start = 6.dp, top = if (first) 2.dp else 14.dp, bottom = 2.dp),
    )
}

@Composable
private fun InfoText(text: String) {
    Text(
        text,
        style = AiryText,
        fontSize = 13.5.sp,
        lineHeight = 19.sp,
        color = Palette.TextSecondary,
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun InfoBox(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text,
            style = AiryText,
            fontSize = 13.5.sp,
            lineHeight = 19.sp,
            color = Palette.TextSecondary,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
