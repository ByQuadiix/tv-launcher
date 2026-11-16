package net.quadiix.tvlauncher.ui

import android.app.ActivityOptions
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.quadiix.tvlauncher.AppEntry
import net.quadiix.tvlauncher.Images
import net.quadiix.tvlauncher.JfItem
import net.quadiix.tvlauncher.LauncherViewModel
import net.quadiix.tvlauncher.startSafely
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/*
 * Aufbau (Spezifikation „tvOS-Style“):
 *  - Obere ~60 %: frei für den Hintergrund. Nur wenn Jellyfin im Dock (oder die Shelf selbst) ausgewählt
 *    ist, erscheinen dort Infos zum Titel und die „Top Shelf“ mit Weiterschauen/Neu.
 *  - Unteres Drittel: Dock mit den Favoriten und darunter die Apps. Die ausgewählte Reihe steht immer
 *    auf derselben Höhe, Reihen darüber blenden aus – so sind nie mehr als 1–2 Reihen zu sehen.
 *  - Statusleiste: nur eine dezente Uhr.
 */

private val SidePadding = 48.dp
/** Oberkante der unteren Liste. */
private val ListTop = 326.dp
/** Auf diese Höhe wird die ausgewählte Kachel der unteren Liste gescrollt. */
private val FocusLine = 342.dp

/** Weichgezeichnetes Hintergrundbild für das Milchglas (siehe [GlassBox]). */
private data class Glass(val bitmap: ImageBitmap?, val filter: ColorFilter?)
private val LocalGlass = staticCompositionLocalOf { Glass(null, null) }

/** Was gerade im Hintergrund zu sehen ist. */
private sealed interface Backdrop {
    data class Sharp(val url: String) : Backdrop
    data class Ambient(val bitmap: ImageBitmap) : Backdrop
    data object Plain : Backdrop
}

@Composable
fun HomeScreen(vm: LauncherViewModel) {
    val showSettings by vm.showSettings.collectAsState()
    val mode by vm.wallpaperMode.collectAsState()
    val custom by vm.wallpaperUrl.collectAsState()
    val featured by vm.featured.collectAsState()

    // Welche Kachel gerade den Ton angibt (für Hintergrund und Shelf)
    var focusedApp by remember { mutableStateOf<AppEntry?>(null) }
    var jellyContext by remember { mutableStateOf(false) }

    val ctx = LocalContext.current
    // Letztes scharfes Bild weichgezeichnet merken – dient als ruhiger Ambient-Hintergrund für Apps ohne Banner
    val sharpUrl = when (mode) {
        "custom" -> custom
        "jellyfin" -> featured?.backdropUrl
        else -> null
    }
    val blurredSharp by produceState<ImageBitmap?>(null, sharpUrl) {
        value = sharpUrl?.let { Images.blurred(ctx, it) } ?: value
    }

    val backdrop: Backdrop = when {
        mode == "custom" && custom != null -> Backdrop.Sharp(custom!!)
        mode == "jellyfin" && jellyContext && featured?.backdropUrl != null && !showSettings ->
            Backdrop.Sharp(featured!!.backdropUrl!!)
        mode == "gradient" -> Backdrop.Plain
        else -> (focusedApp?.ambient ?: blurredSharp)?.let { Backdrop.Ambient(it) } ?: Backdrop.Plain
    }

    // Milchglas zeigt die weiche Fassung dessen, was dahinter liegt
    val glass = when (backdrop) {
        is Backdrop.Sharp -> Glass(remember(blurredSharp) { blurredSharp?.let { Images.tone(it, 0.35f, 0.42f) } }, null)
        is Backdrop.Ambient -> Glass(remember(backdrop.bitmap) { Images.tone(backdrop.bitmap, 0.35f, 0.56f) }, null)
        Backdrop.Plain -> Glass(null, null)
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Color.White, background = Palette.Background)) {
        CompositionLocalProvider(
            LocalContentColor provides Palette.TextPrimary,
            LocalGlass provides glass,
        ) {
            Box(Modifier.fillMaxSize()) {
                Background(backdrop, strongDim = showSettings)
                if (showSettings) {
                    SettingsScreen(vm)
                } else {
                    HomeContent(
                        vm,
                        jellyContext = jellyContext,
                        onAppFocused = { app ->
                            focusedApp = app
                            jellyContext = app?.pkg == LauncherViewModel.JELLYFIN_PKG
                            if (jellyContext) vm.onJellyfinTileFocused()
                        },
                        onShelfFocused = { jellyContext = true },
                    )
                }
                Clock(Modifier.align(Alignment.TopEnd).padding(top = 28.dp, end = SidePadding))
            }
        }
    }
}

// ---------------------------------------------------------------- Hintergrund

private fun dimFilter(alpha: Float) = ColorFilter.tint(Color.Black.copy(alpha = alpha), BlendMode.SrcAtop)

/**
 * GPU-Budget (siehe README): Im Ruhezustand genau EINE Vollbild-Schicht (Foto oder Ambient-Bild).
 *
 * Überblendung ohne Zwischenebenen: Das alte Bild bleibt deckend liegen, nur das neue wird mit
 * steigender Deckkraft direkt darüber gezeichnet (Bild-Alpha, keine Offscreen-Ebene) → während des
 * Wechsels 2 statt vorher 3 Vollbild-Schichten. Außerdem wechselt der Hintergrund erst, wenn man
 * ~0,3 s auf einer Kachel bleibt – beim schnellen Durchblättern wird gar nicht überblendet.
 */
@Composable
private fun Background(backdrop: Backdrop, strongDim: Boolean) {
    val ctx = LocalContext.current
    val sharpDim = remember(strongDim) { dimFilter(if (strongDim) 0.82f else 0.38f) }
    val ambient = if (strongDim) 0.28f else 0.46f

    var current by remember { mutableStateOf<Backdrop?>(null) }
    var previous by remember { mutableStateOf<Backdrop?>(null) }
    val fade = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(backdrop, strongDim) {
        if (current != null && !strongDim) delay(300)   // beim Durchblättern nicht ständig wechseln
        if (backdrop == current) return@LaunchedEffect
        previous = current
        current = backdrop
        fade.snapTo(0f)
        // Scharfe Bilder starten die Einblendung erst, wenn sie geladen sind (siehe onSuccess)
        if (backdrop !is Backdrop.Sharp) {
            fade.animateTo(1f, tween(380))
            previous = null
        }
    }
    val onSharpLoaded: () -> Unit = {
        scope.launch {
            fade.animateTo(1f, tween(380))
            previous = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Grundfläche nur, wenn sonst nichts Deckendes da ist
        if (previous == null && (current == null || current == Backdrop.Plain || fade.value < 1f)) {
            GradientImage(Gradients.wallpaper, Modifier.fillMaxSize())
        }
        previous?.let { BackdropLayer(it, 1f, sharpDim, ambient, null) }
        current?.let { BackdropLayer(it, fade.value, sharpDim, ambient, onSharpLoaded) }
    }
}

@Composable
private fun BackdropLayer(
    b: Backdrop,
    alpha: Float,
    sharpDim: ColorFilter,
    ambientBrightness: Float,
    onLoaded: (() -> Unit)?,
) {
    val ctx = LocalContext.current
    when (b) {
        is Backdrop.Sharp -> {
            val request = remember(b.url) { Images.backdrop(ctx, b.url) }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = sharpDim,
                alpha = alpha,
                onSuccess = { onLoaded?.invoke() },
                modifier = Modifier.fillMaxSize(),
            )
        }
        is Backdrop.Ambient -> {
            // Farbton fest eingerechnet (siehe Images.tone) – kein Farbfilter pro Bild nötig
            val toned = remember(b.bitmap, ambientBrightness) { Images.tone(b.bitmap, 0.35f, ambientBrightness) }
            Image(
                toned, null,
                contentScale = ContentScale.FillBounds,
                alpha = alpha,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Backdrop.Plain -> if (alpha > 0f) GradientImage(Gradients.wallpaper, Modifier.fillMaxSize())
    }
}

/**
 * Milchglas ohne Echtzeit-Unschärfe: zeichnet genau den Ausschnitt der weichgezeichneten
 * Hintergrund-Fassung, der hinter dieser Fläche liegt. Sieht aus wie Gaussian Blur, kostet aber nur
 * ein Bild in der Größe der Fläche.
 */
@Composable
private fun GlassBox(
    modifier: Modifier,
    corner: androidx.compose.ui.unit.Dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val glass = LocalGlass.current
    val view = LocalView.current
    var pos by remember { mutableStateOf(Offset.Zero) }
    val shape = remember(corner) { RoundedCornerShape(corner) }
    Box(
        modifier
            .onGloballyPositioned { pos = it.positionInRoot() }
            .clip(shape)
            .drawBehind {
                val bmp = glass.bitmap
                if (bmp != null) {
                    drawImage(
                        bmp,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(bmp.width, bmp.height),
                        dstOffset = IntOffset(-pos.x.toInt(), -pos.y.toInt()),
                        dstSize = IntSize(view.width, view.height),
                        colorFilter = glass.filter,
                    )
                }
                drawRect(Palette.Glass)
            }
            .border(1.dp, Palette.GlassEdge, shape),
        content = content,
    )
}

// ---------------------------------------------------------------- Startseite

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeContent(
    vm: LauncherViewModel,
    jellyContext: Boolean,
    onAppFocused: (AppEntry?) -> Unit,
    onShelfFocused: () -> Unit,
) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val apps by vm.apps.collectAsState()
    val favs by vm.favorites.collectAsState()
    val loggedIn by vm.jfLoggedIn.collectAsState()
    val jfMessage by vm.jfMessage.collectAsState()
    val shelf by vm.shelf.collectAsState()
    val featured by vm.featured.collectAsState()

    var menuApp by remember { mutableStateOf<AppEntry?>(null) }
    val listState = rememberLazyListState()
    val firstFocus = remember { FocusRequester() }
    val jellyfinTile = remember { FocusRequester() }
    /** Index der Reihe mit dem Fokus – Reihen darüber werden ausgeblendet. */
    var focusedRow by remember { mutableIntStateOf(0) }

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

    // Senkrecht: die ausgewählte Kachel immer auf [FocusLine] – am Ende der Liste hört das Scrollen auf.
    val focusLinePx = with(LocalDensity.current) { FocusLine.toPx() }
    val rowSpec = remember(focusLinePx) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float) =
                offset - focusLinePx
        }
    }

    val launch: (AppEntry, Rect) -> Unit = { app, r -> vm.launchApp(ctx, app, scaleUpOptions(view, r)) }

    Box(Modifier.fillMaxSize()) {
        // ---- Oben: Infos + Top Shelf, nur im Jellyfin-Kontext
        AnimatedVisibility(
            visible = jellyContext,
            enter = fadeIn(tween(350)),
            exit = fadeOut(tween(250)),
        ) {
            Box(Modifier.fillMaxWidth().height(ListTop)) {
                when {
                    !loggedIn -> ContextNote("Jellyfin ist nicht verbunden – OK lang drücken → Launcher-Einstellungen")
                    shelf.isEmpty() -> ContextNote(jfMessage ?: "Lädt …")
                    else -> {
                        TitleInfo(featured, Modifier.align(Alignment.TopStart))
                        Shelf(
                            shelf,
                            Modifier.align(Alignment.BottomStart),
                            down = jellyfinTile.takeIf { favApps.any { it.pkg == LauncherViewModel.JELLYFIN_PKG } },
                            onFocused = { item ->
                                onShelfFocused()
                                vm.onShelfItemFocused(item)
                            },
                            onClick = { item, r -> vm.openInJellyfin(ctx, item, scaleUpOptions(view, r)) },
                        )
                    }
                }
            }
        }

        // ---- Unten: Dock + Apps
        CompositionLocalProvider(LocalBringIntoViewSpec provides rowSpec) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = ListTop, bottom = 40.dp),
            ) {
                item(key = "dock") {
                    FadingRow(0, focusedRow, { focusedRow = it }) {
                        GlassBox(Modifier.padding(horizontal = 30.dp).fillMaxWidth(), corner = 30.dp) {
                            CalmLazyRow(
                                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(18.dp),
                            ) {
                                if (favApps.isEmpty()) {
                                    item {
                                        HintCard(
                                            "Noch keine Favoriten",
                                            "Bei einer App OK gedrückt halten → „Zu Favoriten hinzufügen“",
                                            Modifier.focusRequester(firstFocus),
                                            onFocusChange = { if (it) onAppFocused(null) },
                                        )
                                    }
                                }
                                itemsIndexed(favApps, key = { _, a -> "fav_${a.pkg}" }) { i, app ->
                                    var m: Modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier
                                    if (app.pkg == LauncherViewModel.JELLYFIN_PKG) m = m.focusRequester(jellyfinTile)
                                    AppTile(
                                        app, m,
                                        onClick = { r -> launch(app, r) },
                                        onLongClick = { menuApp = app },
                                        onFocusChange = { if (it) onAppFocused(app) },
                                    )
                                }
                            }
                        }
                    }
                }

                itemsIndexed(appRows, key = { _, row -> "apps_${row.first().pkg}" }) { i, rowApps ->
                    FadingRow(i + 1, focusedRow, { focusedRow = it }) {
                        Column(Modifier.padding(horizontal = SidePadding)) {
                            if (i == 0) {
                                Text(
                                    "Apps",
                                    style = AiryText,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Palette.TextSecondary,
                                    modifier = Modifier.padding(top = 22.dp, bottom = 12.dp, start = 4.dp),
                                )
                            } else {
                                Spacer(Modifier.height(8.dp))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(17.dp)) {
                                rowApps.forEach { app ->
                                    AppTile(
                                        app,
                                        onClick = { r -> launch(app, r) },
                                        onLongClick = { menuApp = app },
                                        onFocusChange = { if (it) onAppFocused(app) },
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "launcher_settings") {
                    FadingRow(appRows.size + 1, focusedRow, { focusedRow = it }) {
                        Column(Modifier.padding(horizontal = SidePadding)) {
                            Spacer(Modifier.height(8.dp))
                            SettingsTile(
                                onClick = { vm.openSettings() },
                                onFocusChange = { if (it) onAppFocused(null) },
                            )
                        }
                    }
                }
            }
        }
    }

    menuApp?.let { app -> AppMenu(app, vm) { menuApp = null } }
}

/** Reihe der unteren Liste, die ausblendet, sobald die Auswahl darunter liegt. */
@Composable
private fun FadingRow(index: Int, focusedRow: Int, setFocused: (Int) -> Unit, content: @Composable () -> Unit) {
    val alpha by animateFloatAsState(if (index < focusedRow) 0f else 1f, tween(260), label = "rowFade")
    Box(
        Modifier
            .onFocusChanged { if (it.hasFocus) setFocused(index) }
            .graphicsLayer { this.alpha = alpha }
    ) { content() }
}

/** Zoom-Animation aus der Kachel heraus beim Öffnen einer App. */
private fun scaleUpOptions(view: View, r: Rect): Bundle? =
    if (r.width <= 0f) null
    else ActivityOptions.makeScaleUpAnimation(
        view, r.left.toInt(), r.top.toInt(), r.width.toInt(), r.height.toInt()
    ).toBundle()

// ---------------------------------------------------------------- Oben: Infos & Shelf

@Composable
private fun ContextNote(text: String) {
    Text(
        text,
        style = AiryText,
        fontSize = 15.sp,
        color = Palette.TextSecondary,
        modifier = Modifier.padding(start = SidePadding, top = 230.dp),
    )
}

/** Logo bzw. Titel, Infozeile und Beschreibung des ausgewählten Titels. */
@Composable
private fun TitleInfo(item: JfItem?, modifier: Modifier) {
    val ctx = LocalContext.current
    Box(modifier.fillMaxWidth().height(172.dp)) {
        // Weicher Schatten hinter dem Text (vorgerechnete Bitmap, siehe Gradients)
        GradientImage(
            Gradients.heroScrim,
            Modifier.align(Alignment.BottomStart).requiredWidth(820.dp).requiredHeight(300.dp),
        )
        AnimatedContent(
            targetState = item,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
            contentKey = { it?.id },
            label = "titleInfo",
            modifier = Modifier.align(Alignment.BottomStart),
        ) { it ->
            if (it == null) return@AnimatedContent
            Column(Modifier.padding(start = SidePadding, bottom = 6.dp).width(560.dp)) {
                if (it.logoUrl != null) {
                    AsyncImage(
                        model = remember(it.logoUrl) { Images.logo(ctx, it.logoUrl) },
                        contentDescription = it.title,
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.BottomStart,
                        modifier = Modifier.size(280.dp, 78.dp),
                    )
                } else {
                    Text(
                        heroTitle(it),
                        style = AiryText,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Palette.TextPrimary,
                    )
                }
                Spacer(Modifier.height(8.dp))
                val sub = it.subtitle.takeIf { s -> (it.logoUrl != null || s != heroTitle(it)) && !it.meta.contains(s) }
                val info = listOf(sub, it.meta).filter { s -> !s.isNullOrEmpty() }.joinToString("  ·  ")
                if (info.isNotEmpty()) {
                    Text(info, style = AiryText, fontSize = 13.sp, color = Palette.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                it.overview?.let { o ->
                    Spacer(Modifier.height(4.dp))
                    Text(o, style = AiryText, fontSize = 12.5.sp, lineHeight = 17.sp, color = Palette.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Bei Folgen ist der Serienname der große Titel, die Folge steht in der Infozeile. */
private fun heroTitle(item: JfItem): String =
    if (item.subtitle.contains(" · S")) item.subtitle.substringBefore(" · ") else item.title

@Composable
private fun Shelf(
    items: List<JfItem>,
    modifier: Modifier,
    down: FocusRequester?,
    onFocused: (JfItem) -> Unit,
    onClick: (JfItem, Rect) -> Unit,
) {
    Column(modifier) {
        Text(
            "Weiterschauen & Neu",
            style = AiryText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Palette.TextSecondary,
            modifier = Modifier.padding(start = SidePadding + 4.dp, bottom = 2.dp),
        )
        CalmLazyRow(
            contentPadding = PaddingValues(start = SidePadding, end = SidePadding, top = 8.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            items(items, key = { it.id }) { item ->
                ShelfCard(
                    item,
                    // Von der Shelf nach unten immer zurück auf die Jellyfin-Kachel
                    if (down != null) Modifier.focusProperties { this.down = down } else Modifier,
                    onFocused = { onFocused(item) },
                    onClick = { r -> onClick(item, r) },
                )
            }
        }
    }
}

@Composable
private fun ShelfCard(item: JfItem, modifier: Modifier, onFocused: () -> Unit, onClick: (Rect) -> Unit) {
    val ctx = LocalContext.current
    FocusCard(
        210.dp, 118.dp, modifier,
        onClick = onClick,
        onFocused = onFocused,
        corner = 16.dp,
        background = if (item.imageUrl != null) Color.Transparent else Palette.Surface,
    ) {
        if (item.imageUrl != null) {
            AsyncImage(
                model = remember(item.imageUrl) { Images.card(ctx, item.imageUrl) },
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        GradientImage(Gradients.bottomScrim, Modifier.fillMaxWidth().fillMaxHeight(0.6f).align(Alignment.BottomCenter))
        Column(Modifier.align(Alignment.BottomStart).padding(start = 12.dp, end = 12.dp, bottom = 10.dp)) {
            if (!item.imageHasTitle) {
                if (item.logoUrl != null) {
                    AsyncImage(
                        model = remember(item.logoUrl) { Images.logo(ctx, item.logoUrl) },
                        contentDescription = item.title,
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.BottomStart,
                        modifier = Modifier.size(120.dp, 32.dp),
                    )
                } else {
                    Text(heroTitle(item), style = AiryText, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Palette.TextPrimary)
                }
                Spacer(Modifier.height(4.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val p = item.progress
                if (p != null) {
                    Box(Modifier.width(54.dp).height(3.dp).background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(p.coerceIn(0f, 1f)).background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(2.dp)))
                    }
                    Spacer(Modifier.width(8.dp))
                }
                val episode = item.subtitle.substringAfter(" · ", "").takeIf { it.startsWith("S") }
                val label = listOfNotNull(episode, item.remaining).joinToString(" · ")
                if (label.isNotEmpty()) Text(label, style = AiryText, fontSize = 11.sp, maxLines = 1, color = Palette.TextPrimary)
            }
        }
    }
}

// ---------------------------------------------------------------- Uhr

/** Einzige Statusanzeige: dezente Uhrzeit (50 %), aktualisiert sich einmal pro Minute. */
@Composable
private fun Clock(modifier: Modifier) {
    val ctx = LocalContext.current
    var now by remember { mutableStateOf(LocalDateTime.now().withSecond(0).withNano(0)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L + 50L)
            now = LocalDateTime.now().withSecond(0).withNano(0)
        }
    }
    val is24 = DateFormat.is24HourFormat(ctx)
    val fmt = remember(is24) { DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm a") }
    Text(
        now.format(fmt),
        style = AiryText,
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium,
        color = Color.White.copy(alpha = 0.5f),
        modifier = modifier,
    )
}

// ---------------------------------------------------------------- Kacheln

/** App-Kachel 16:9; der Name blendet nur unter der ausgewählten Kachel mit 60 % ein. */
@Composable
private fun AppTile(
    app: AppEntry,
    modifier: Modifier = Modifier,
    onClick: (Rect) -> Unit,
    onLongClick: () -> Unit,
    onFocusChange: (Boolean) -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val labelAlpha by animateFloatAsState(if (focused) 0.6f else 0f, tween(220), label = "label")
    Column(Modifier.width(156.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FocusCard(
            156.dp, 88.dp, modifier,
            onClick = onClick,
            onLongClick = onLongClick,
            onFocusChange = { focused = it; onFocusChange(it) },
            background = if (app.banner != null) Color.Transparent else Palette.Surface,
        ) { isFocused ->
            val banner = app.banner
            if (banner != null) {
                Image(
                    banner, app.label,
                    contentScale = ContentScale.Crop,
                    colorFilter = if (isFocused) null else UnfocusedDim,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(
                    Modifier.fillMaxSize().padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    app.icon?.let {
                        Image(it, null, Modifier.size(38.dp), colorFilter = if (isFocused) null else UnfocusedDim)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(app.label, style = AiryText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Palette.TextSecondary)
                }
            }
        }
        Text(
            app.label,
            style = AiryText,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = Color.White.copy(alpha = labelAlpha),
            modifier = Modifier.padding(top = 9.dp).height(20.dp),
        )
    }
}

/** Kachel am Ende der Apps: öffnet die Launcher-Einstellungen (statt eines Zahnrads auf der Startseite). */
@Composable
private fun SettingsTile(onClick: () -> Unit, onFocusChange: (Boolean) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val labelAlpha by animateFloatAsState(if (focused) 0.6f else 0f, tween(220), label = "label")
    Column(Modifier.width(156.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FocusCard(
            156.dp, 88.dp,
            onClick = { onClick() },
            onFocusChange = { focused = it; onFocusChange(it) },
        ) { isFocused ->
            Text(
                "⚙",
                fontSize = 30.sp,
                color = Color.White.copy(alpha = if (isFocused) 0.85f else 0.55f),
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Text(
            "Launcher-Einstellungen",
            style = AiryText,
            fontSize = 13.sp,
            maxLines = 1,
            color = Color.White.copy(alpha = labelAlpha),
            modifier = Modifier.padding(top = 9.dp).height(20.dp),
        )
    }
}

@Composable
private fun HintCard(title: String, text: String, modifier: Modifier, onFocusChange: (Boolean) -> Unit) {
    Column(Modifier.padding(bottom = 29.dp)) {
        FocusCard(
            380.dp, 88.dp, modifier,
            onClick = {},
            onFocusChange = onFocusChange,
            focusScale = 1.04f,
            background = Color.White.copy(alpha = 0.06f),
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp), verticalArrangement = Arrangement.Center) {
                Text(title, style = AiryText, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Palette.TextPrimary)
                Text(text, style = AiryText, fontSize = 12.sp, color = Palette.TextSecondary, maxLines = 2)
            }
        }
    }
}

// ---------------------------------------------------------------- Waagerechte Reihen

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
            offset >= 0f && trailing <= containerSize -> 0f
            size > containerSize - 2 * margin -> offset - margin
            offset < 0f -> offset - margin
            else -> trailing - containerSize + margin
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CalmLazyRow(
    contentPadding: PaddingValues,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: LazyListScope.() -> Unit,
) {
    CompositionLocalProvider(LocalBringIntoViewSpec provides CalmBringIntoView) {
        LazyRow(contentPadding = contentPadding, horizontalArrangement = horizontalArrangement, content = content)
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
                .background(Palette.Surface.copy(alpha = 0.97f), RoundedCornerShape(26.dp))
                .border(1.dp, Palette.GlassEdge, RoundedCornerShape(26.dp))
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
                            .clip(RoundedCornerShape(10.dp)),
                    )
                    Spacer(Modifier.width(14.dp))
                }
                Column {
                    Text(app.label, style = AiryText, fontSize = 19.sp, fontWeight = FontWeight.Medium, color = Palette.TextPrimary)
                    if (idx >= 0) {
                        Text("Favorit ${idx + 1} von ${favs.size}", style = AiryText, fontSize = 13.sp, color = Palette.TextSecondary)
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
            TvButton("Launcher-Einstellungen", Modifier.fillMaxWidth()) {
                onDismiss()
                vm.openSettings()
            }
        }
        LaunchedEffect(Unit) {
            delay(50)
            runCatching { first.requestFocus() }
        }
    }
}
