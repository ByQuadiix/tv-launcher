package net.quadiix.tvlauncher.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.bitmapConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.quadiix.tvlauncher.AppEntry
import net.quadiix.tvlauncher.JfItem
import net.quadiix.tvlauncher.LauncherViewModel
import net.quadiix.tvlauncher.WeatherClient
import net.quadiix.tvlauncher.startSafely
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val SidePadding = 48.dp
private val HeroHeight = 286.dp

/** Weicher Schatten unter Text, damit er auf hellen Filmbildern lesbar bleibt (kostet nur Glyphen-Fläche). */
private val TextShade = Shadow(Color(0x99000000), Offset(0f, 2f), 10f)
/** Harter, unscharfer-loser Schatten: praktisch kostenlos, hilft trotzdem auf hellen Bildern. */
private val TextShadeCheap = Shadow(Color(0x80000000), Offset(0f, 2f), 0f)

@Composable
fun HomeScreen(vm: LauncherViewModel) {
    val showSettings by vm.showSettings.collectAsState()
    val listState = rememberLazyListState()
    // Oben ist das Filmbild hell wie bei Apple TV; sobald man nach unten scrollt (oder in den
    // Einstellungen ist), wird es abgedunkelt. Das passiert über den Farbfilter des Bildes selbst,
    // also ohne zusätzliche Vollbild-Schicht.
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 120 } }
    val dim by animateFloatAsState(
        when {
            showSettings -> 0.86f
            scrolled -> 0.72f
            else -> 0.28f
        },
        tween(350),
        label = "dim",
    )

    MaterialTheme(colorScheme = darkColorScheme(primary = Color.White)) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Box(Modifier.fillMaxSize()) {
                Wallpaper(vm, dim)
                if (showSettings) SettingsScreen(vm) else HomeContent(vm, listState, scrolled)
            }
        }
    }
}

// ---------------------------------------------------------------- Hintergrund

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun Wallpaper(vm: LauncherViewModel, dim: Float) {
    val mode by vm.wallpaperMode.collectAsState()
    val custom by vm.wallpaperUrl.collectAsState()
    val backdrop by vm.backdrop.collectAsState()
    val url = when (mode) {
        "jellyfin" -> backdrop
        "custom" -> custom
        else -> null
    }
    val ctx = LocalContext.current
    val dimFilter = remember(dim) { ColorFilter.tint(Color.Black.copy(alpha = dim), BlendMode.SrcAtop) }

    // GPU-Budget (siehe README): Im Ruhezustand wird genau EINE Vollbild-Schicht gemalt – das Filmbild.
    // Der Farbverlauf darunter (und das schwarze Fenster, siehe MainActivity) kommt nur dazu, solange
    // kein Bild da ist oder gerade überblendet wird.
    var imageShown by remember { mutableStateOf(false) }
    LaunchedEffect(url) { if (url == null) imageShown = false }
    val transition = updateTransition(url, label = "wallpaper")
    Box(Modifier.fillMaxSize()) {
        if (!imageShown || transition.currentState != transition.targetState) {
            GradientImage(Gradients.wallpaper, Modifier.fillMaxSize())
        }
        transition.Crossfade(animationSpec = tween(500)) { u ->
            if (u != null) {
                val request = remember(u) {
                    ImageRequest.Builder(ctx)
                        .data(u)
                        .size(1280, 720)
                        .bitmapConfig(Bitmap.Config.RGB_565)
                        .build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = dimFilter,
                    onSuccess = { imageShown = true },
                    onError = { imageShown = false },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // Kein Verlauf über den ganzen Bildschirm (kostete ~0,6 Vollbild-Schichten); die Lesbarkeit
        // kommt vom Farbfilter des Bildes und einem kleinen Verlauf nur hinter dem Hero-Text.
    }
}

// ---------------------------------------------------------------- Startseite

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeContent(vm: LauncherViewModel, listState: LazyListState, scrolled: Boolean) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val apps by vm.apps.collectAsState()
    val favs by vm.favorites.collectAsState()
    val loggedIn by vm.jfLoggedIn.collectAsState()
    val jfMessage by vm.jfMessage.collectAsState()
    val resume by vm.resume.collectAsState()
    val nextUp by vm.nextUp.collectAsState()
    val latest by vm.latest.collectAsState()
    val views by vm.views.collectAsState()

    var menuApp by remember { mutableStateOf<AppEntry?>(null) }
    val firstFocus = remember { FocusRequester() }

    val appsLoaded = apps.isNotEmpty()
    LaunchedEffect(appsLoaded) {
        delay(80)
        runCatching { firstFocus.requestFocus() }
    }
    LaunchedEffect(Unit) {
        vm.homeEvents.collect {
            listState.animateScrollToItem(0)
            runCatching { firstFocus.requestFocus() }
        }
    }

    val favApps = remember(apps, favs) { favs.mapNotNull { p -> apps.firstOrNull { it.pkg == p } } }
    val appRows = remember(apps) { apps.chunked(5) }
    // Merkt sich, ob gerade etwas im Dock ausgewählt ist (für das Scroll-Verhalten unten)
    val dockFocused = remember { mutableStateOf(false) }
    val onDockFocus: (Boolean) -> Unit = { dockFocused.value = it }
    val heroPx = with(LocalDensity.current) { HeroHeight.toPx() }

    /*
     * Senkrechtes Scrollen: Das ausgewählte Element wandert in die Bildschirmmitte. Ganz oben und
     * ganz unten hört die Liste von selbst auf, dort bleibt das Element dann außerhalb der Mitte.
     * Im Dock steht die Seite immer ganz oben, damit der große Bereich mit dem Filmbild sichtbar ist.
     * Compose fragt diese Funktion während des Scrollens immer wieder ab, deshalb wird alles relativ
     * zur aktuellen Position berechnet – so gibt es nur eine einzige, durchgehende Bewegung.
     */
    val centerSpec = remember(listState, heroPx) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                if (dockFocused.value) {
                    val off = listState.firstVisibleItemScrollOffset.toFloat()
                    return if (listState.firstVisibleItemIndex == 0) -off else -(off + heroPx)
                }
                return offset + size / 2f - containerSize / 2f
            }
        }
    }

    CompositionLocalProvider(LocalBringIntoViewSpec provides centerSpec) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 56.dp),
        ) {
            item(key = "hero") { Hero(vm) }

            item(key = "dock") {
                Dock {
                    if (favApps.isEmpty()) {
                        item {
                            HintCard(
                                "Noch keine Favoriten",
                                "Unten bei „Apps“ OK gedrückt halten → „Zu Favoriten hinzufügen“",
                                Modifier.focusRequester(firstFocus),
                                onFocusChange = onDockFocus,
                            ) {}
                        }
                    }
                    itemsIndexed(favApps, key = { _, a -> "fav_${a.pkg}" }) { i, app ->
                        AppTile(
                            app,
                            if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                            onClick = { vm.launchApp(ctx, app) },
                            onLongClick = { menuApp = app },
                            onFocusChange = onDockFocus,
                        )
                    }
                }
            }

            if (!loggedIn) {
                item(key = "jf_connect") {
                    Column {
                        SectionTitle("Jellyfin")
                        CalmLazyRow(contentPadding = PaddingValues(horizontal = SidePadding, vertical = 14.dp)) {
                            item {
                                HintCard(
                                    "Jellyfin verbinden",
                                    jfMessage ?: "Anmelden, um Filme und Serien hier zu sehen",
                                ) { vm.openSettings() }
                            }
                        }
                    }
                }
            } else {
                jfMessage?.let { msg ->
                    item(key = "jf_msg") {
                        Text(
                            msg,
                            color = Color(0xFFFFB4A9),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = SidePadding, vertical = 6.dp),
                        )
                    }
                }
                if (resume.isNotEmpty()) item(key = "resume") {
                    LandscapeRow("Weiterschauen", resume, vm)
                }
                if (nextUp.isNotEmpty()) item(key = "nextup") {
                    LandscapeRow("Nächste Folgen", nextUp, vm)
                }
                if (latest.isNotEmpty()) item(key = "latest") {
                    PosterRow("Neu hinzugefügt", latest, vm)
                }
                if (views.isNotEmpty()) item(key = "views") {
                    LibraryRow("Mediathek", views, vm)
                }
            }

            item(key = "apps_title") { SectionTitle("Apps") }
            items(appRows, key = { "apps_${it.first().pkg}" }) { rowApps ->
                Row(
                    Modifier.padding(horizontal = SidePadding, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(17.dp),
                ) {
                    rowApps.forEach { app ->
                        AppTile(
                            app,
                            onClick = { vm.launchApp(ctx, app) },
                            onLongClick = { menuApp = app },
                        )
                    }
                }
            }
        }

        // Uhr & Zahnrad verschwinden beim Scrollen, damit sie nicht über den Karten liegen
        AnimatedVisibility(
            visible = !scrolled,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            TopBar(vm, below = firstFocus)
        }
    }
    }

    menuApp?.let { app -> AppMenu(app, vm) { menuApp = null } }
}

/**
 * Waagerechtes Scrollen in den Reihen: nur scrollen, wenn die Karte nicht ganz sichtbar ist, dann mit
 * etwas Rand (12 %). Ersetzt das TV-Standardverhalten von Compose, das jede Karte auf ~30 % der
 * Breite schiebt, auch wenn sie schon sichtbar ist.
 */
@OptIn(ExperimentalFoundationApi::class)
private val CalmBringIntoView = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val margin = containerSize * 0.12f
        val trailing = offset + size
        return when {
            offset >= 0f && trailing <= containerSize -> 0f          // schon ganz sichtbar
            size > containerSize - 2 * margin -> offset - margin     // größer als der Platz: oben ausrichten
            offset < 0f -> offset - margin                           // ragt oben/links hinaus
            else -> trailing - containerSize + margin                // ragt unten/rechts hinaus
        }
    }
}

// ---------------------------------------------------------------- Oben: Hero + Pillen

/** Großer Bereich oben: Logo bzw. Titel, Infozeile und Beschreibung des ausgewählten Titels. */
@Composable
private fun Hero(vm: LauncherViewModel) {
    val featured by vm.featured.collectAsState()
    Box(Modifier.fillMaxWidth().height(HeroHeight)) {
        // Weicher Schatten hinter dem Text. Er ist höher als der Hero-Bereich (requiredHeight) und ragt
        // oben und unten darüber hinaus, damit nirgends eine Kante zu sehen ist.
        GradientImage(
            Gradients.heroScrim,
            // Höhe 460 dp in einem 286 dp hohen Bereich: Compose zentriert sie, also ragt sie oben und
            // unten je 87 dp hinaus. Der dunkelste Punkt (62 % der Höhe) liegt damit auf Höhe des Textes.
            Modifier.align(Alignment.BottomStart).requiredWidth(860.dp).requiredHeight(460.dp),
        )
        AnimatedContent(
            targetState = featured,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
            contentKey = { it?.id },
            label = "hero",
            modifier = Modifier.align(Alignment.BottomStart),
        ) { item ->
            if (item != null) {
                Column(Modifier.padding(start = SidePadding, bottom = 18.dp).width(520.dp)) {
                    if (item.logoUrl != null) {
                        AsyncImage(
                            model = item.logoUrl,
                            contentDescription = item.title,
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.BottomStart,
                            modifier = Modifier.size(300.dp, 86.dp),
                        )
                    } else {
                        Text(
                            heroTitle(item),
                            style = TextStyle(shadow = TextShadeCheap),
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = Color.White,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    // Untertitel weglassen, wenn er nur wiederholt, was schon dasteht (z. B. das Jahr bei Filmen)
                    val sub = item.subtitle.takeIf {
                        (item.logoUrl != null || it != heroTitle(item)) && !item.meta.contains(it)
                    }
                    val info = listOf(sub, item.meta)
                        .filter { !it.isNullOrEmpty() }.joinToString("  ·  ")
                    if (info.isNotEmpty()) {
                        Text(
                            info,
                            style = TextStyle(shadow = TextShadeCheap),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    item.overview?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            it,
                            style = TextStyle(shadow = TextShadeCheap),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            } else {
                Spacer(Modifier.size(1.dp))
            }
        }
    }
}

/** Bei Folgen ist der Serienname der große Titel, die Folge steht in der Infozeile. */
private fun heroTitle(item: JfItem): String =
    if (item.subtitle.contains(" · S")) item.subtitle.substringBefore(" · ") else item.title

@Composable
private fun TopBar(vm: LauncherViewModel, below: FocusRequester, modifier: Modifier = Modifier) {
    val weather by vm.weather.collectAsState()
    val weatherEnabled by vm.weatherEnabled.collectAsState()
    Row(
        modifier.padding(top = 22.dp, end = SidePadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val w = weather
        if (weatherEnabled && w != null) {
            val (icon, _) = WeatherClient.describe(w.code, w.isDay)
            Pill { Text("$icon ${w.temp}°", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White) }
        }
        Pill { Clock() }
        FocusCard(
            40.dp, 40.dp,
            // Die Leiste liegt über der Liste; ohne festes Ziel findet Compose nach unten nichts
            // und der Fokus bliebe auf dem Zahnrad hängen.
            Modifier.focusProperties { down = below },
            onClick = { vm.openSettings() },
            corner = 20.dp,
            background = Color.White.copy(alpha = 0.16f),
        ) { focused ->
            Text(
                "⚙",
                fontSize = 20.sp,
                color = if (focused) Color.White else Color.White.copy(alpha = 0.85f),
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun Pill(content: @Composable () -> Unit) {
    Box(
        Modifier
            .height(40.dp)
            .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Uhr: wird nur einmal pro Minute neu gezeichnet. */
@Composable
private fun Clock() {
    val ctx = LocalContext.current
    var now by remember { mutableStateOf(LocalDateTime.now().withSecond(0).withNano(0)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L + 50L)
            now = LocalDateTime.now().withSecond(0).withNano(0)
        }
    }
    val is24 = DateFormat.is24HourFormat(ctx)
    val timeFmt = remember(is24) { DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm a") }
    Text(now.format(timeFmt), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
}

// ---------------------------------------------------------------- Dock

/** Glas-Leiste für die Favoriten. Echte Unschärfe wäre für die TV-GPU zu teuer – halbtransparentes Weiß wirkt ähnlich. */
@Composable
private fun Dock(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    val shape = RoundedCornerShape(30.dp)
    Box(
        Modifier
            .padding(horizontal = 30.dp)
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.13f), shape)
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape),
    ) {
        CalmLazyRow(
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            content = content,
        )
    }
}

// ---------------------------------------------------------------- Kacheln

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 21.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.White.copy(alpha = 0.78f),
        modifier = Modifier.padding(start = SidePadding, top = 22.dp),
    )
}

/** App-Kachel mit abgerundeten Ecken; der Name erscheint darunter, wenn sie ausgewählt ist. */
@Composable
private fun AppTile(
    app: AppEntry,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocusChange: (Boolean) -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(156.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FocusCard(
            156.dp, 88.dp, modifier,
            onClick = onClick,
            onLongClick = onLongClick,
            onFocusChange = { focused = it; onFocusChange(it) },
            corner = 16.dp,
            background = if (app.banner != null) Color.Transparent else Color(0xFF2A2A30),
        ) {
            val banner = app.banner
            if (banner != null) {
                Image(banner, app.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Column(
                    Modifier.fillMaxSize().padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    app.icon?.let { Image(it, null, Modifier.size(40.dp)) }
                    Spacer(Modifier.height(4.dp))
                    Text(app.label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White)
                }
            }
        }
        Text(
            if (focused) app.label else "",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = Color.White,
            modifier = Modifier.padding(top = 10.dp).height(20.dp),
        )
    }
}

@Composable
private fun LandscapeRow(title: String, items: List<JfItem>, vm: LauncherViewModel) {
    val ctx = LocalContext.current
    Column {
        SectionTitle(title)
        CalmLazyRow(
            contentPadding = PaddingValues(horizontal = SidePadding, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            items(items, key = { it.id }) { item ->
                ContinueCard(item, onClick = { vm.openInJellyfin(ctx, item) }, onFocused = { vm.onItemFocused(item) })
            }
        }
    }
}

/** Querformat-Karte mit Logo/Titel, Fortschritt und Restzeit direkt im Bild. */
@Composable
private fun ContinueCard(item: JfItem, onClick: () -> Unit, onFocused: () -> Unit) {
    FocusCard(
        272.dp, 153.dp,
        onClick = onClick,
        onFocused = onFocused,
        corner = 16.dp,
        focusScale = 1.08f,
        background = if (item.imageUrl != null) Color.Transparent else Color(0xFF2A2A30),
    ) {
        if (item.imageUrl != null) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Abdunklung nur im unteren Teil der Karte (kleine Fläche → günstig)
        GradientImage(Gradients.bottomScrim, Modifier.fillMaxWidth().fillMaxHeight(0.6f).align(Alignment.BottomCenter))
        Column(Modifier.align(Alignment.BottomStart).padding(start = 14.dp, end = 14.dp, bottom = 12.dp)) {
            if (item.imageHasTitle) {
                // Titel ist im Bild schon enthalten
            } else if (item.logoUrl != null) {
                AsyncImage(
                    model = item.logoUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.BottomStart,
                    modifier = Modifier.size(150.dp, 40.dp),
                )
            } else {
                Text(
                    heroTitle(item),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White,
                )
            }
            if (!item.imageHasTitle) Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("▶", fontSize = 11.sp, color = Color.White)
                Spacer(Modifier.width(8.dp))
                val p = item.progress
                if (p != null) {
                    Box(
                        Modifier.width(64.dp).height(4.dp)
                            .background(Color.White.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
                    ) {
                        Box(
                            Modifier.fillMaxHeight().fillMaxWidth(p.coerceIn(0f, 1f))
                                .background(Color.White, RoundedCornerShape(2.dp))
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                val episode = item.subtitle.substringAfter(" · ", "").takeIf { it.startsWith("S") }
                val label = listOfNotNull(episode, item.remaining ?: item.meta.takeIf { it.isNotEmpty() && p == null && episode == null })
                    .joinToString(" · ")
                Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, color = Color.White)
            }
        }
    }
}

@Composable
private fun PosterRow(title: String, items: List<JfItem>, vm: LauncherViewModel) {
    val ctx = LocalContext.current
    Column {
        SectionTitle(title)
        CalmLazyRow(
            contentPadding = PaddingValues(horizontal = SidePadding, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            items(items, key = { it.id }) { item ->
                PosterCard(item, onClick = { vm.openInJellyfin(ctx, item) }, onFocused = { vm.onItemFocused(item) })
            }
        }
    }
}

@Composable
private fun PosterCard(item: JfItem, onClick: () -> Unit, onFocused: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(132.dp)) {
        FocusCard(
            132.dp, 198.dp,
            onClick = onClick,
            onFocused = onFocused,
            onFocusChange = { focused = it },
            corner = 12.dp,
            focusScale = 1.08f,
            background = if ((item.posterUrl ?: item.imageUrl) != null) Color.Transparent else Color(0xFF2A2A30),
        ) {
            val url = item.posterUrl ?: item.imageUrl
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    item.title,
                    fontSize = 14.sp,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center).padding(10.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            heroTitle(item),
            fontSize = 13.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = Color.White.copy(alpha = if (focused) 1f else 0.7f),
        )
    }
}

@Composable
private fun LibraryRow(title: String, items: List<JfItem>, vm: LauncherViewModel) {
    val ctx = LocalContext.current
    Column {
        SectionTitle(title)
        CalmLazyRow(
            contentPadding = PaddingValues(horizontal = SidePadding, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            items(items, key = { it.id }) { item ->
                FocusCard(
                    208.dp, 117.dp,
                    onClick = { vm.openInJellyfin(ctx, item) },
                    corner = 14.dp,
                    background = if (item.imageUrl != null) Color.Transparent else Color(0xFF2A2A30),
                ) {
                    if (item.imageUrl != null) {
                        AsyncImage(
                            model = item.imageUrl,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(
                            item.title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HintCard(
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    onFocusChange: (Boolean) -> Unit = {},
    onClick: () -> Unit,
) {
    Column(Modifier.padding(bottom = 30.dp)) {
        FocusCard(
            380.dp, 88.dp, modifier,
            onClick = onClick,
            onFocusChange = onFocusChange,
            corner = 16.dp,
            focusScale = 1.05f,
            background = Color.White.copy(alpha = 0.14f),
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp), verticalArrangement = Arrangement.Center) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text(text, fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f), maxLines = 2)
            }
        }
    }
}

// ---------------------------------------------------------------- App-Menü

@Composable
private fun AppMenu(app: AppEntry, vm: LauncherViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val favs by vm.favorites.collectAsState()
    val idx = favs.indexOf(app.pkg)
    val first = remember { FocusRequester() }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(420.dp)
                .background(Color(0xF22A2A30), RoundedCornerShape(24.dp))
                .padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val pic = app.banner ?: app.icon
                if (pic != null) {
                    Image(
                        pic, null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(if (app.banner != null) 80.dp else 45.dp, 45.dp)
                            .background(Color.Black, RoundedCornerShape(10.dp)),
                    )
                    Spacer(Modifier.width(14.dp))
                }
                Column {
                    Text(app.label, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    if (idx >= 0) {
                        Text("Favorit ${idx + 1} von ${favs.size}", fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            TvButton(
                if (idx >= 0) "Aus Favoriten entfernen" else "Zu Favoriten hinzufügen",
                Modifier.fillMaxWidth().focusRequester(first),
            ) {
                vm.toggleFavorite(app.pkg)
                onDismiss()
            }
            if (idx >= 0) {
                TvButton("Nach links verschieben", Modifier.fillMaxWidth()) { vm.moveFavorite(app.pkg, -1) }
                TvButton("Nach rechts verschieben", Modifier.fillMaxWidth()) { vm.moveFavorite(app.pkg, 1) }
            }
            TvButton("Öffnen", Modifier.fillMaxWidth()) {
                onDismiss()
                vm.launchApp(ctx, app)
            }
            TvButton("App-Info", Modifier.fillMaxWidth()) {
                onDismiss()
                startSafely(
                    ctx,
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            TvButton("Deinstallieren", Modifier.fillMaxWidth()) {
                onDismiss()
                startSafely(
                    ctx,
                    Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.pkg}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
        LaunchedEffect(Unit) {
            delay(50)
            runCatching { first.requestFocus() }
        }
    }
}

/** Waagerechte Reihe mit dem ruhigen Scroll-Verhalten (siehe [CalmBringIntoView]). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CalmLazyRow(
    contentPadding: PaddingValues,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    CompositionLocalProvider(LocalBringIntoViewSpec provides CalmBringIntoView) {
        LazyRow(contentPadding = contentPadding, horizontalArrangement = horizontalArrangement, content = content)
    }
}
