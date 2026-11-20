package net.quadiix.tvlauncher.ui

import android.app.ActivityOptions
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.view.KeyEvent as AndroidKeyEvent
import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.quadiix.tvlauncher.AppEntry
import net.quadiix.tvlauncher.Images
import net.quadiix.tvlauncher.JfItem
import net.quadiix.tvlauncher.LauncherViewModel
import net.quadiix.tvlauncher.NavEvent
import net.quadiix.tvlauncher.ShelfCategory
import net.quadiix.tvlauncher.TvInputEntry
import net.quadiix.tvlauncher.startSafely
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/*
 * Aufbau im Apple-TV-/tvOS-Stil (1080p-UI = 960×540 dp, optimiert für 55" QD-Mini LED & MT5896):
 *  - Exaktes 5-Spalten-Raster: 49 + 5×158 + 4×18 + 49 = 960 dp (vollständig symmetrisch).
 *  - Obere Stage (322 dp): frei für Ambient/Hintergrund. Nur wenn Jellyfin im Dock (oder die Shelf)
 *    fokussiert ist, erscheinen oben Infos zum Titel, die 3 Kategorie-Tabs (Weitergucken / Als Nächstes / Neu)
 *    und die passende Top Shelf.
 *  - Zweistufige App-Liste: Im Dock bleibt oben Platz für Stage/Hintergrund; beim Runterscrollen in
 *    „Apps“ gleitet das Dock über die Clip-Kante (y = 64 dp) hinaus und gibt den ganzen Bildschirm für
 *    3+ App-Reihen gleichzeitig frei.
 *  - Obere rechte Leiste: gut lesbare Uhr + dezenter Einstellungs-Knopf (optional + HDMI).
 */

private val SidePadding = 49.dp
private val TileWidth = 158.dp
private val TileHeight = 89.dp
private val TileGap = 18.dp
private val DockOuterPadding = 31.dp // 31 + 18 inner = 49 dp (exakt bündig mit dem App-Raster)

private val TopBarHeight = 64.dp
private val ListTop = 322.dp
private val ListInnerTopPadding = 258.dp // 64 + 258 = 322 dp

private enum class FocusArea { DOCK, SHELF_CARDS, SHELF_TABS, TOP_BAR, APP_GRID }

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

    // Zustand bleibt über Öffnen/Schließen der Einstellungen erhalten (kein Fokus-/Scroll-Verlust)
    var focusedApp by remember { mutableStateOf<AppEntry?>(null) }
    var jellyContext by remember { mutableStateOf(false) }
    var focusedRow by remember { mutableIntStateOf(0) }
    var lastDockPkg by remember { mutableStateOf<String?>(null) }
    var settingsOpenedFromTopBar by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val dockListState = rememberLazyListState()

    val ctx = LocalContext.current
    val sharpUrl = when (mode) {
        "custom" -> custom
        "jellyfin" -> featured?.backdropUrl
        else -> null
    }
    val blurredSharp by produceState<ImageBitmap?>(null, sharpUrl) {
        value = sharpUrl?.let { Images.blurred(ctx, it) } ?: value
    }

    val showSharpJellyfin = mode == "jellyfin" && jellyContext && focusedRow == 0 &&
        featured?.backdropUrl != null && !showSettings

    val backdrop: Backdrop = when {
        mode == "custom" && custom != null -> Backdrop.Sharp(custom!!)
        showSharpJellyfin -> Backdrop.Sharp(featured!!.backdropUrl!!)
        mode == "gradient" -> Backdrop.Plain
        else -> (focusedApp?.ambient ?: blurredSharp)?.let { Backdrop.Ambient(it) } ?: Backdrop.Plain
    }

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
                        vm = vm,
                        listState = listState,
                        dockListState = dockListState,
                        jellyContext = jellyContext,
                        focusedRow = focusedRow,
                        lastDockPkg = lastDockPkg,
                        restoreTopBarFocus = settingsOpenedFromTopBar,
                        onClearRestoreTopBar = { settingsOpenedFromTopBar = false },
                        onFocusedRowChange = { focusedRow = it },
                        onLastDockPkgChange = { lastDockPkg = it },
                        onAppFocused = { app, fromDock ->
                            focusedApp = app
                            val isJf = fromDock && app?.pkg == LauncherViewModel.JELLYFIN_PKG
                            jellyContext = isJf
                            if (isJf) vm.onJellyfinTileFocused()
                        },
                        onShelfFocused = { jellyContext = true },
                        onOpenSettings = { fromTopBar ->
                            settingsOpenedFromTopBar = fromTopBar
                            vm.openSettings()
                        },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Hintergrund

private fun dimFilter(alpha: Float) = ColorFilter.tint(Color.Black.copy(alpha = alpha), BlendMode.SrcAtop)

/**
 * GPU-Budget (MediaTek MT5896): Im Ruhezustand genau EINE Vollbild-Schicht.
 * Überblendung ohne Zwischenebenen: altes Bild bleibt deckend, neues blendet per Bild-Alpha ein.
 */
@Composable
private fun Background(backdrop: Backdrop, strongDim: Boolean) {
    val sharpDim = remember(strongDim) { dimFilter(if (strongDim) 0.84f else 0.40f) }
    val ambient = if (strongDim) 0.24f else 0.44f

    var current by remember { mutableStateOf<Backdrop?>(null) }
    var previous by remember { mutableStateOf<Backdrop?>(null) }
    val fade = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(backdrop, strongDim) {
        if (current != null && !strongDim) delay(280)
        if (backdrop == current) return@LaunchedEffect
        previous = current
        current = backdrop
        fade.snapTo(0f)
        if (backdrop !is Backdrop.Sharp) {
            fade.animateTo(1f, tween(360))
            previous = null
        }
    }
    val onSharpLoaded: () -> Unit = {
        scope.launch {
            fade.animateTo(1f, tween(360))
            previous = null
        }
    }

    Box(Modifier.fillMaxSize()) {
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
 * Hintergrund-Fassung, der hinter dieser Fläche liegt.
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
    listState: LazyListState,
    dockListState: LazyListState,
    jellyContext: Boolean,
    focusedRow: Int,
    lastDockPkg: String?,
    restoreTopBarFocus: Boolean,
    onClearRestoreTopBar: () -> Unit,
    onFocusedRowChange: (Int) -> Unit,
    onLastDockPkgChange: (String?) -> Unit,
    onAppFocused: (AppEntry?, Boolean) -> Unit,
    onShelfFocused: () -> Unit,
    onOpenSettings: (fromTopBar: Boolean) -> Unit,
) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val apps by vm.apps.collectAsState()
    val favs by vm.favorites.collectAsState()
    val hiddenApps by vm.hiddenApps.collectAsState()
    val hideFavsInAll by vm.hideFavoritesInAllApps.collectAsState()
    val showHdmiQuick by vm.showHdmiQuickAction.collectAsState()
    val tvInputs by vm.tvInputs.collectAsState()

    val loggedIn by vm.jfLoggedIn.collectAsState()
    val jfMessage by vm.jfMessage.collectAsState()
    val jfLoading by vm.jfLoading.collectAsState()
    val jfResume by vm.jfResume.collectAsState()
    val jfNextUp by vm.jfNextUp.collectAsState()
    val jfLatest by vm.jfLatest.collectAsState()
    val activeCategory by vm.activeCategory.collectAsState()
    val featured by vm.featured.collectAsState()

    val activeShelfItems = when (activeCategory) {
        ShelfCategory.RESUME -> jfResume
        ShelfCategory.NEXT_UP -> jfNextUp
        ShelfCategory.LATEST -> jfLatest
    }

    val visibleApps = remember(apps, hiddenApps) { apps.filter { it.pkg !in hiddenApps } }
    val favApps = remember(visibleApps, favs) {
        favs.mapNotNull { p -> visibleApps.firstOrNull { it.pkg == p } }
    }
    val gridApps = remember(visibleApps, favs, hideFavsInAll) {
        if (hideFavsInAll) visibleApps.filterNot { it.pkg in favs } else visibleApps
    }
    val appRows = remember(gridApps) { gridApps.chunked(5) }

    var menuApp by remember { mutableStateOf<AppEntry?>(null) }
    var menuShelfItem by remember { mutableStateOf<JfItem?>(null) }
    var showHdmiSheet by remember { mutableStateOf(false) }
    var movingPkg by remember { mutableStateOf<String?>(null) }
    var focusArea by remember { mutableStateOf(FocusArea.DOCK) }

    // Stabile FocusRequester für deterministische D-Pad-Übergänge
    val firstDockFocus = remember { FocusRequester() }
    val jellyfinTileFocus = remember { FocusRequester() }
    val firstGridFocus = remember { FocusRequester() }
    val shelfFirstFocus = remember { FocusRequester() }
    val tabRequesters = remember { ShelfCategory.entries.associateWith { FocusRequester() } }
    val topSettingsFocus = remember { FocusRequester() }
    val topHdmiFocus = remember { FocusRequester() }
    val dockRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val gridRequesters = remember { mutableMapOf<String, FocusRequester>() }

    // Welcher FocusRequester nach Schließen eines Side-Sheets wieder fokussiert wird
    var restoreAfterSheet by remember { mutableStateOf<FocusRequester?>(null) }

    val showHdmiButton = showHdmiQuick && tvInputs.isNotEmpty()
    val topBarEntryFocus = if (showHdmiButton) topHdmiFocus else topSettingsFocus
    val hasJellyfinInDock = remember(favApps) { favApps.any { it.pkg == LauncherViewModel.JELLYFIN_PKG } }

    fun focusDock(pkg: String? = lastDockPkg) {
        val validPkg = pkg?.takeIf { p -> favApps.any { it.pkg == p } }
        val target = validPkg?.let { dockRequesters[it] } ?: firstDockFocus
        runCatching { target.requestFocus() }.onFailure { runCatching { firstDockFocus.requestFocus() } }
    }

    // Startfokus / Fokus-Wiederherstellung nach Rückkehr aus den Einstellungen
    val appsLoaded = apps.isNotEmpty()
    LaunchedEffect(appsLoaded) {
        delay(70)
        when {
            restoreTopBarFocus -> {
                onClearRestoreTopBar()
                runCatching { topSettingsFocus.requestFocus() }.onFailure { focusDock() }
            }
            focusedRow == 0 -> focusDock()
            else -> runCatching { firstGridFocus.requestFocus() }.onFailure { focusDock() }
        }
    }

    // Kontextabhängige Home- und Zurück-Taste
    LaunchedEffect(Unit) {
        vm.navEvents.collect { ev ->
            when (ev) {
                NavEvent.HOME -> {
                    movingPkg = null
                    menuApp = null
                    menuShelfItem = null
                    showHdmiSheet = false
                    onFocusedRowChange(0)
                    listState.animateScrollToItem(0)
                    dockListState.animateScrollToItem(0)
                    delay(40)
                    runCatching { firstDockFocus.requestFocus() }
                }
                NavEvent.BACK -> {
                    when {
                        movingPkg != null -> movingPkg = null
                        menuApp != null || menuShelfItem != null || showHdmiSheet -> {
                            menuApp = null
                            menuShelfItem = null
                            showHdmiSheet = false
                            delay(40)
                            val r = restoreAfterSheet
                            restoreAfterSheet = null
                            runCatching { r?.requestFocus() }.onFailure { focusDock() }
                        }
                        focusArea == FocusArea.SHELF_CARDS || focusArea == FocusArea.SHELF_TABS -> {
                            runCatching {
                                if (hasJellyfinInDock) jellyfinTileFocus.requestFocus() else focusDock()
                            }
                        }
                        focusArea == FocusArea.TOP_BAR -> {
                            if (focusedRow > 0) runCatching { firstGridFocus.requestFocus() }
                            else focusDock()
                        }
                        focusedRow > 0 -> {
                            onFocusedRowChange(0)
                            listState.animateScrollToItem(0)
                            delay(40)
                            focusDock()
                        }
                        else -> {
                            dockListState.animateScrollToItem(0)
                            runCatching { firstDockFocus.requestFocus() }
                        }
                    }
                }
            }
        }
    }

    // Zweistufiges vertikales Scrollen:
    //  - Im Dock (row 0): Kachel auf 272 dp innerhalb der Liste (= 336 dp am Bildschirm) → oben frei für Stage.
    //  - In „Apps“ (row >= 1): Kachel auf 56 dp (= 120 dp am Bildschirm) → Dock scrollt weg, 3+ Reihen sichtbar!
    val density = LocalDensity.current
    val dockFocusOffsetPx = with(density) { 272.dp.toPx() }
    val gridFocusOffsetPx = with(density) { 56.dp.toPx() }
    val rowSpec = remember(focusedRow, dockFocusOffsetPx, gridFocusOffsetPx) {
        val target = if (focusedRow == 0) dockFocusOffsetPx else gridFocusOffsetPx
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float) =
                offset - target
        }
    }

    val launch: (AppEntry, Rect) -> Unit = { app, r -> vm.launchApp(ctx, app, scaleUpOptions(view, r)) }
    val showStage = jellyContext && focusedRow == 0

    // Overflow-Indikatoren im Milchglas-Dock
    val canScrollDockLeft by remember { derivedStateOf { dockListState.canScrollBackward } }
    val canScrollDockRight by remember { derivedStateOf { dockListState.canScrollForward } }

    Box(Modifier.fillMaxSize()) {
        // ---- Oben: Jellyfin Hero + Kategorie-Tabs + Top Shelf (nur bei Fokus auf Jellyfin im Dock)
        AnimatedVisibility(
            visible = showStage,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(200)),
        ) {
            Box(Modifier.fillMaxWidth().height(ListTop)) {
                when {
                    !loggedIn -> ContextNote("Jellyfin ist nicht verbunden – oben rechts oder „OK lang drücken“ → Einstellungen")
                    else -> {
                        TitleInfo(featured, Modifier.align(Alignment.TopStart))
                        ShelfSection(
                            activeCategory = activeCategory,
                            items = activeShelfItems,
                            counts = mapOf(
                                ShelfCategory.RESUME to jfResume.size,
                                ShelfCategory.NEXT_UP to jfNextUp.size,
                                ShelfCategory.LATEST to jfLatest.size,
                            ),
                            isLoading = jfLoading && !vm.hasAnyShelfItems(),
                            errorMessage = jfMessage.takeIf { !vm.hasAnyShelfItems() },
                            tabRequesters = tabRequesters,
                            firstCardRequester = shelfFirstFocus,
                            downToDock = if (hasJellyfinInDock) jellyfinTileFocus else firstDockFocus,
                            rightToTopBar = topBarEntryFocus,
                            modifier = Modifier.align(Alignment.BottomStart),
                            onTabFocused = { cat ->
                                focusArea = FocusArea.SHELF_TABS
                                onShelfFocused()
                                vm.selectCategory(cat, immediateFeature = false)
                            },
                            onTabClick = { cat ->
                                vm.selectCategory(cat, immediateFeature = true)
                                if (vm.itemsFor(cat).isNotEmpty()) {
                                    scope.launch {
                                        delay(40)
                                        runCatching { shelfFirstFocus.requestFocus() }
                                    }
                                }
                            },
                            onCardFocused = { item ->
                                focusArea = FocusArea.SHELF_CARDS
                                onShelfFocused()
                                vm.onShelfItemFocused(item)
                            },
                            onCardClick = { item, r -> vm.openInJellyfin(ctx, item, scaleUpOptions(view, r)) },
                            onCardLongClick = { item ->
                                restoreAfterSheet = shelfFirstFocus
                                menuShelfItem = item
                            },
                        )
                    }
                }
            }
        }

        // ---- Unterer Bereich ab y = 64 dp (clippt gescrollte Apps sauber unterhalb der Top-Bar)
        CompositionLocalProvider(LocalBringIntoViewSpec provides rowSpec) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = TopBarHeight),
                contentPadding = PaddingValues(top = ListInnerTopPadding, bottom = 72.dp),
            ) {
                // Item 0: Milchglas-Dock
                item(key = "dock") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .onFocusChanged {
                                if (it.hasFocus) {
                                    onFocusedRowChange(0)
                                    focusArea = FocusArea.DOCK
                                }
                            }
                    ) {
                        Box(Modifier.fillMaxWidth()) {
                            GlassBox(
                                Modifier
                                    .padding(horizontal = DockOuterPadding)
                                    .fillMaxWidth(),
                                corner = 28.dp,
                            ) {
                                CalmLazyRow(
                                    state = dockListState,
                                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(TileGap),
                                ) {
                                    if (favApps.isEmpty()) {
                                        item(key = "empty_fav") {
                                            HintCard(
                                                "Noch keine Favoriten",
                                                "Bei einer App „OK“ gedrückt halten → „Zu Favoriten hinzufügen“",
                                                Modifier
                                                    .focusRequester(firstDockFocus)
                                                    .focusProperties { up = topBarEntryFocus },
                                                onFocusChange = { if (it) onAppFocused(null, true) },
                                            )
                                        }
                                    }
                                    itemsIndexed(favApps, key = { _, a -> "fav_${a.pkg}" }) { i, app ->
                                        val tileReq = dockRequesters.getOrPut(app.pkg) { FocusRequester() }
                                        val isMoving = movingPkg == app.pkg
                                        val isJf = app.pkg == LauncherViewModel.JELLYFIN_PKG
                                        var m: Modifier = Modifier.focusRequester(tileReq)
                                        if (i == 0) m = m.focusRequester(firstDockFocus)
                                        if (isJf) m = m.focusRequester(jellyfinTileFocus)

                                        m = m.onPreviewKeyEvent { ev ->
                                            val native = ev.nativeKeyEvent
                                            if (isMoving) {
                                                if (ev.type == KeyEventType.KeyDown) {
                                                    when (native.keyCode) {
                                                        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                                                            vm.moveFavorite(app.pkg, -1)
                                                            true
                                                        }
                                                        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                                                            vm.moveFavorite(app.pkg, 1)
                                                            true
                                                        }
                                                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                                                        AndroidKeyEvent.KEYCODE_ENTER,
                                                        AndroidKeyEvent.KEYCODE_BACK -> {
                                                            movingPkg = null
                                                            true
                                                        }
                                                        AndroidKeyEvent.KEYCODE_DPAD_UP,
                                                        AndroidKeyEvent.KEYCODE_DPAD_DOWN -> true
                                                        else -> false
                                                    }
                                                } else {
                                                    native.keyCode in setOf(
                                                        AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                                                        AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
                                                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                                                        AndroidKeyEvent.KEYCODE_ENTER,
                                                        AndroidKeyEvent.KEYCODE_DPAD_UP,
                                                        AndroidKeyEvent.KEYCODE_DPAD_DOWN,
                                                    )
                                                }
                                            } else {
                                                if (ev.type == KeyEventType.KeyDown && native.keyCode == AndroidKeyEvent.KEYCODE_DPAD_UP) {
                                                    if (isJf && loggedIn) {
                                                        val target = if (activeShelfItems.isNotEmpty()) {
                                                            shelfFirstFocus
                                                        } else {
                                                            tabRequesters.getValue(activeCategory)
                                                        }
                                                        runCatching { target.requestFocus() }
                                                            .onFailure { runCatching { topBarEntryFocus.requestFocus() } }
                                                        true
                                                    } else {
                                                        runCatching { topBarEntryFocus.requestFocus() }
                                                        true
                                                    }
                                                } else {
                                                    false
                                                }
                                            }
                                        }

                                        AppTile(
                                            app = app,
                                            modifier = m,
                                            isMoving = isMoving,
                                            canMoveLeft = i > 0,
                                            canMoveRight = i < favApps.lastIndex,
                                            onClick = { r ->
                                                if (isMoving) movingPkg = null else launch(app, r)
                                            },
                                            onLongClick = {
                                                if (isMoving) {
                                                    movingPkg = null
                                                } else {
                                                    restoreAfterSheet = tileReq
                                                    menuApp = app
                                                }
                                            },
                                            onFocusChange = { focused ->
                                                if (focused) {
                                                    onLastDockPkgChange(app.pkg)
                                                    onAppFocused(app, true)
                                                }
                                            },
                                        )
                                    }
                                }

                                // Dezente Overflow-Pfeile an den Rändern des Glas-Docks bei >5 Favoriten
                                if (canScrollDockLeft) {
                                    DockEdgeChevron(TvIcons.ChevronLeft, Modifier.align(Alignment.CenterStart).padding(start = 4.dp))
                                }
                                if (canScrollDockRight) {
                                    DockEdgeChevron(TvIcons.ChevronRight, Modifier.align(Alignment.CenterEnd).padding(end = 4.dp))
                                }
                            }
                        }

                        // Kompakter Hinweis im Live-Verschiebemodus
                        AnimatedVisibility(visible = movingPkg != null, enter = fadeIn(tween(180)), exit = fadeOut(tween(150))) {
                            Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                                Row(
                                    Modifier
                                        .clip(CircleShape)
                                        .background(Palette.SheetSurface)
                                        .border(1.dp, Palette.GlassEdge, CircleShape)
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(TvIcons.MoveHorizontal, null, tint = Palette.TextPrimary, modifier = Modifier.size(16.dp))
                                    Text(
                                        "Links / Rechts zum Verschieben  ·  OK oder Zurück fertig",
                                        style = AiryText,
                                        fontSize = 13.5.sp,
                                        color = Palette.TextPrimary,
                                    )
                                }
                            }
                        }
                    }
                }

                // Items 1..N: Symmetrisches 5-Spalten-App-Raster (nutzt beim Scrollen den gesamten Bildschirm)
                itemsIndexed(appRows, key = { _, row -> "apps_${row.first().pkg}" }) { rowIdx, rowApps ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SidePadding)
                            .onFocusChanged {
                                if (it.hasFocus) {
                                    onFocusedRowChange(rowIdx + 1)
                                    focusArea = FocusArea.APP_GRID
                                }
                            }
                    ) {
                        if (rowIdx == 0) {
                            Text(
                                "Apps",
                                style = AiryText,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Palette.TextSecondary,
                                modifier = Modifier.padding(top = 20.dp, bottom = 12.dp, start = 2.dp),
                            )
                        } else {
                            Spacer(Modifier.height(10.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                            rowApps.forEachIndexed { colIdx, app ->
                                val req = gridRequesters.getOrPut(app.pkg) { FocusRequester() }
                                var m: Modifier = Modifier.focusRequester(req)
                                if (rowIdx == 0 && colIdx == 0) m = m.focusRequester(firstGridFocus)
                                if (rowIdx == 0) {
                                    // Zuverlässiger Rückweg von Zeile 0 der Apps zurück ins Dock
                                    m = m.onPreviewKeyEvent { ev ->
                                        if (ev.type == KeyEventType.KeyDown && ev.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_UP) {
                                            scope.launch {
                                                onFocusedRowChange(0)
                                                listState.animateScrollToItem(0)
                                                delay(35)
                                                focusDock()
                                            }
                                            true
                                        } else false
                                    }
                                }
                                AppTile(
                                    app = app,
                                    modifier = m,
                                    onClick = { r -> launch(app, r) },
                                    onLongClick = {
                                        restoreAfterSheet = req
                                        menuApp = app
                                    },
                                    onFocusChange = { if (it) onAppFocused(app, false) },
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---- Oben rechts: Dezente Status- & Aktionsleiste (optional HDMI + Einstellungen + Uhr)
        TopBar(
            showHdmi = showHdmiButton,
            hdmiFocus = topHdmiFocus,
            settingsFocus = topSettingsFocus,
            leftTarget = if (showStage && loggedIn) tabRequesters.getValue(ShelfCategory.LATEST) else null,
            onFocused = { focusArea = FocusArea.TOP_BAR },
            onDown = {
                when {
                    focusedRow > 0 -> runCatching { firstGridFocus.requestFocus() }
                    showStage && loggedIn -> {
                        val target = if (activeShelfItems.isNotEmpty()) shelfFirstFocus else tabRequesters.getValue(activeCategory)
                        runCatching { target.requestFocus() }.onFailure { focusDock() }
                    }
                    else -> focusDock()
                }
            },
            onOpenHdmi = {
                restoreAfterSheet = topHdmiFocus
                vm.refreshTvInputs()
                showHdmiSheet = true
            },
            onOpenSettings = { onOpenSettings(true) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 18.dp, end = SidePadding),
        )

        // ---- Rechte Side-Sheets (App-Menü, Shelf-Menü, HDMI-Auswahl)
        val sheetVisible = menuApp != null || menuShelfItem != null || showHdmiSheet
        AnimatedVisibility(
            visible = sheetVisible,
            enter = fadeIn(tween(180)) + slideInHorizontally(tween(220)) { it / 3 },
            exit = fadeOut(tween(150)) + slideOutHorizontally(tween(180)) { it / 3 },
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            val closeSheet: () -> Unit = {
                menuApp = null
                menuShelfItem = null
                showHdmiSheet = false
                scope.launch {
                    delay(40)
                    val target = restoreAfterSheet
                    restoreAfterSheet = null
                    runCatching { target?.requestFocus() }.onFailure { focusDock() }
                }
            }

            when {
                menuApp != null -> AppSideSheet(
                    app = menuApp!!,
                    vm = vm,
                    onStartMove = { pkg ->
                        menuApp = null
                        movingPkg = pkg
                        scope.launch {
                            onFocusedRowChange(0)
                            listState.animateScrollToItem(0)
                            delay(50)
                            focusDock(pkg)
                        }
                    },
                    onOpenSettings = {
                        menuApp = null
                        onOpenSettings(false)
                    },
                    onDismiss = closeSheet,
                )
                menuShelfItem != null -> ShelfSideSheet(
                    item = menuShelfItem!!,
                    vm = vm,
                    onOpenSettings = {
                        menuShelfItem = null
                        onOpenSettings(false)
                    },
                    onDismiss = closeSheet,
                )
                showHdmiSheet -> HdmiSideSheet(
                    inputs = tvInputs,
                    onSelect = { input ->
                        showHdmiSheet = false
                        vm.openTvInput(ctx, input)
                    },
                    onDismiss = closeSheet,
                )
            }
        }
    }
}

/** Zoom-Animation aus der Kachel heraus beim Öffnen einer App. */
private fun scaleUpOptions(view: View, r: Rect): Bundle? =
    if (r.width <= 0f) null
    else ActivityOptions.makeScaleUpAnimation(
        view, r.left.toInt(), r.top.toInt(), r.width.toInt(), r.height.toInt()
    ).toBundle()

// ---------------------------------------------------------------- Obere Leiste (Uhr & Schnellzugriff)

@Composable
private fun TopBar(
    showHdmi: Boolean,
    hdmiFocus: FocusRequester,
    settingsFocus: FocusRequester,
    leftTarget: FocusRequester?,
    onFocused: () -> Unit,
    onDown: () -> Unit,
    onOpenHdmi: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier,
) {
    val downInterceptor = Modifier.onPreviewKeyEvent { ev ->
        if (ev.type == KeyEventType.KeyDown && ev.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN) {
            onDown()
            true
        } else false
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (showHdmi) {
            TvIconButton(
                icon = TvIcons.TvInput,
                contentDescription = "HDMI-Eingänge",
                modifier = Modifier
                    .focusRequester(hdmiFocus)
                    .then(downInterceptor)
                    .focusProperties {
                        right = settingsFocus
                        if (leftTarget != null) left = leftTarget
                    },
                onFocused = onFocused,
                onClick = onOpenHdmi,
            )
        }
        TvIconButton(
            icon = TvIcons.Settings,
            contentDescription = "Launcher-Einstellungen",
            modifier = Modifier
                .focusRequester(settingsFocus)
                .then(downInterceptor)
                .focusProperties {
                    if (showHdmi) left = hdmiFocus
                    else if (leftTarget != null) left = leftTarget
                    right = FocusRequester.Cancel
                },
            onFocused = onFocused,
            onClick = onOpenSettings,
        )
        Spacer(Modifier.width(4.dp))
        Clock()
    }
}

/** Dezente, aus 3 m gut lesbare Uhrzeit, aktualisiert sich einmal pro Minute. */
@Composable
private fun Clock(modifier: Modifier = Modifier) {
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
        fontSize = 21.sp,
        fontWeight = FontWeight.Medium,
        color = Color.White.copy(alpha = 0.78f),
        modifier = modifier,
    )
}

// ---------------------------------------------------------------- Oben: Jellyfin Hero & Kategorien-Shelf

@Composable
private fun ContextNote(text: String) {
    Text(
        text,
        style = AiryText,
        fontSize = 15.sp,
        color = Palette.TextSecondary,
        modifier = Modifier.padding(start = SidePadding, top = 236.dp),
    )
}

/** Logo bzw. Titel, Infozeile und Beschreibung des ausgewählten Titels. */
@Composable
private fun TitleInfo(item: JfItem?, modifier: Modifier) {
    val ctx = LocalContext.current
    Box(modifier.fillMaxWidth().height(152.dp)) {
        GradientImage(
            Gradients.heroScrim,
            Modifier.align(Alignment.BottomStart).requiredWidth(820.dp).requiredHeight(280.dp),
        )
        AnimatedContent(
            targetState = item,
            transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(180)) },
            contentKey = { it?.id },
            label = "titleInfo",
            modifier = Modifier.align(Alignment.BottomStart),
        ) { it ->
            if (it == null) return@AnimatedContent
            Column(Modifier.padding(start = SidePadding, top = 16.dp, bottom = 6.dp).width(580.dp)) {
                if (it.logoUrl != null) {
                    AsyncImage(
                        model = remember(it.logoUrl) { Images.logo(ctx, it.logoUrl) },
                        contentDescription = it.title,
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.BottomStart,
                        modifier = Modifier.widthIn(max = 260.dp).heightIn(max = 56.dp),
                    )
                } else {
                    Text(
                        heroTitle(it),
                        style = AiryText,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Palette.TextPrimary,
                    )
                }
                Spacer(Modifier.height(6.dp))
                val sub = it.subtitle.takeIf { s -> (it.logoUrl != null || s != heroTitle(it)) && !it.meta.contains(s) }
                val info = listOf(sub, it.meta, it.remaining).filter { s -> !s.isNullOrEmpty() }.joinToString("  ·  ")
                if (info.isNotEmpty()) {
                    Text(
                        info,
                        style = AiryText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Palette.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                it.overview?.let { o ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        o,
                        style = AiryText,
                        fontSize = 13.5.sp,
                        lineHeight = 18.sp,
                        color = Palette.TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun heroTitle(item: JfItem): String =
    if (item.subtitle.contains(" · S")) item.subtitle.substringBefore(" · ") else item.title

@Composable
private fun ShelfSection(
    activeCategory: ShelfCategory,
    items: List<JfItem>,
    counts: Map<ShelfCategory, Int>,
    isLoading: Boolean,
    errorMessage: String?,
    tabRequesters: Map<ShelfCategory, FocusRequester>,
    firstCardRequester: FocusRequester,
    downToDock: FocusRequester,
    rightToTopBar: FocusRequester,
    modifier: Modifier,
    onTabFocused: (ShelfCategory) -> Unit,
    onTabClick: (ShelfCategory) -> Unit,
    onCardFocused: (JfItem) -> Unit,
    onCardClick: (JfItem, Rect) -> Unit,
    onCardLongClick: (JfItem) -> Unit,
) {
    val shelfRowState = rememberLazyListState()
    LaunchedEffect(activeCategory) {
        shelfRowState.scrollToItem(0)
    }

    Column(modifier.fillMaxWidth()) {
        // ---- Separate Kategorie-Tabs: Weitergucken · Als Nächstes · Neu
        Row(
            modifier = Modifier.padding(start = SidePadding, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShelfCategory.entries.forEachIndexed { idx, cat ->
                val count = counts[cat] ?: 0
                val isLast = idx == ShelfCategory.entries.lastIndex
                TvCategoryPill(
                    text = cat.label,
                    active = activeCategory == cat,
                    dimmed = count == 0 && !isLoading,
                    modifier = Modifier
                        .focusRequester(tabRequesters.getValue(cat))
                        .focusProperties {
                            down = if (items.isNotEmpty()) firstCardRequester else downToDock
                            if (isLast) right = rightToTopBar
                        },
                    onFocused = { onTabFocused(cat) },
                    onClick = { onTabClick(cat) },
                )
            }
        }

        // ---- Kartenreihe der gewählten Kategorie
        Box(Modifier.fillMaxWidth().height(128.dp), contentAlignment = Alignment.CenterStart) {
            when {
                isLoading -> {
                    Row(
                        modifier = Modifier.padding(horizontal = SidePadding),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        repeat(4) {
                            Box(
                                Modifier
                                    .size(196.dp, 110.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = 0.06f))
                                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
                            )
                        }
                    }
                }
                errorMessage != null -> {
                    Text(
                        errorMessage,
                        style = AiryText,
                        fontSize = 14.5.sp,
                        color = Palette.TextSecondary,
                        modifier = Modifier.padding(start = SidePadding),
                    )
                }
                items.isEmpty() -> {
                    Text(
                        "Gerade nichts in „${activeCategory.label}“",
                        style = AiryText,
                        fontSize = 14.5.sp,
                        color = Palette.TextSecondary,
                        modifier = Modifier.padding(start = SidePadding),
                    )
                }
                else -> {
                    CalmLazyRow(
                        state = shelfRowState,
                        contentPadding = PaddingValues(start = SidePadding, end = SidePadding, top = 6.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        itemsIndexed(items, key = { _, it -> "${activeCategory.name}_${it.id}" }) { idx, item ->
                            val activeTabReq = tabRequesters.getValue(activeCategory)
                            var m: Modifier = Modifier.focusProperties {
                                up = activeTabReq
                                down = downToDock
                            }
                            if (idx == 0) m = m.focusRequester(firstCardRequester)
                            ShelfCard(
                                item = item,
                                category = activeCategory,
                                modifier = m,
                                onFocused = { onCardFocused(item) },
                                onClick = { r -> onCardClick(item, r) },
                                onLongClick = { onCardLongClick(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShelfCard(
    item: JfItem,
    category: ShelfCategory,
    modifier: Modifier,
    onFocused: () -> Unit,
    onClick: (Rect) -> Unit,
    onLongClick: () -> Unit,
) {
    val ctx = LocalContext.current
    FocusCard(
        width = 196.dp,
        height = 110.dp,
        modifier = modifier,
        onClick = onClick,
        onLongClick = onLongClick,
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
        GradientImage(Gradients.bottomScrim, Modifier.fillMaxWidth().fillMaxHeight(0.62f).align(Alignment.BottomCenter))
        Column(Modifier.align(Alignment.BottomStart).padding(start = 12.dp, end = 12.dp, bottom = 9.dp)) {
            if (!item.imageHasTitle) {
                if (item.logoUrl != null) {
                    AsyncImage(
                        model = remember(item.logoUrl) { Images.logo(ctx, item.logoUrl) },
                        contentDescription = item.title,
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.BottomStart,
                        modifier = Modifier.size(118.dp, 30.dp),
                    )
                } else {
                    Text(
                        heroTitle(item),
                        style = AiryText,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Palette.TextPrimary,
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val p = item.progress
                if (p != null) {
                    Box(
                        Modifier
                            .width(58.dp)
                            .height(4.dp)
                            .background(Color.White.copy(alpha = 0.28f), RoundedCornerShape(2.dp))
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(p.coerceIn(0f, 1f))
                                .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(2.dp))
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                val episode = item.subtitle.substringAfter(" · ", "").takeIf { it.startsWith("S") }
                val label = when (category) {
                    ShelfCategory.RESUME -> listOfNotNull(episode, item.remaining).joinToString(" · ")
                    ShelfCategory.NEXT_UP -> episode ?: item.subtitle.takeIf { it.isNotBlank() } ?: ""
                    ShelfCategory.LATEST -> episode ?: item.subtitle.takeIf { it.isNotBlank() } ?: ""
                }
                if (label.isNotEmpty()) {
                    Text(
                        label,
                        style = AiryText,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Palette.TextPrimary,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- App-Kacheln & Dock-Helfer

/** App-Kachel 16:9; Name erscheint bei Fokus klar lesbar (14 sp, 92 %) darunter. */
@Composable
private fun AppTile(
    app: AppEntry,
    modifier: Modifier = Modifier,
    isMoving: Boolean = false,
    canMoveLeft: Boolean = false,
    canMoveRight: Boolean = false,
    onClick: (Rect) -> Unit,
    onLongClick: () -> Unit,
    onFocusChange: (Boolean) -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val showLabel = focused || isMoving
    val labelAlpha by animateFloatAsState(if (showLabel) 0.92f else 0f, tween(200), label = "label")
    val cardBg = remember(app.banner, app.cardTint) {
        when {
            app.banner != null -> Color.Transparent
            app.cardTint != null -> Color(app.cardTint)
            else -> Palette.Surface
        }
    }

    Column(Modifier.width(TileWidth), horizontalAlignment = Alignment.CenterHorizontally) {
        FocusCard(
            width = TileWidth,
            height = TileHeight,
            modifier = modifier,
            onClick = onClick,
            onLongClick = onLongClick,
            onFocusChange = { focused = it; onFocusChange(it) },
            isMoving = isMoving,
            background = cardBg,
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
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (app.icon != null) {
                        Image(
                            app.icon,
                            contentDescription = app.label,
                            modifier = Modifier.size(50.dp),
                            colorFilter = if (isFocused) null else UnfocusedDim,
                        )
                    } else {
                        Text(
                            app.label,
                            style = AiryText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = Palette.TextPrimary,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                }
            }

            if (isMoving) {
                if (canMoveLeft) {
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 6.dp)
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.65f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(TvIcons.ChevronLeft, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
                if (canMoveRight) {
                    Box(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 6.dp)
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.65f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(TvIcons.ChevronRight, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        Text(
            app.label,
            style = AiryText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = Color.White.copy(alpha = labelAlpha),
            modifier = Modifier.padding(top = 8.dp).height(22.dp),
        )
    }
}

@Composable
private fun DockEdgeChevron(icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Box(
        modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.42f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.72f), modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun HintCard(title: String, text: String, modifier: Modifier, onFocusChange: (Boolean) -> Unit) {
    Column(Modifier.padding(bottom = 30.dp)) {
        FocusCard(
            width = 410.dp,
            height = TileHeight,
            modifier = modifier,
            onClick = {},
            onFocusChange = onFocusChange,
            focusScale = 1.04f,
            background = Color.White.copy(alpha = 0.06f),
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.Center) {
                Text(title, style = AiryText, fontSize = 15.5.sp, fontWeight = FontWeight.Medium, color = Palette.TextPrimary)
                Spacer(Modifier.height(2.dp))
                Text(text, style = AiryText, fontSize = 13.sp, color = Palette.TextSecondary, maxLines = 2)
            }
        }
    }
}

// ---------------------------------------------------------------- Waagerechte Reihen

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
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: LazyListScope.() -> Unit,
) {
    CompositionLocalProvider(LocalBringIntoViewSpec provides CalmBringIntoView) {
        LazyRow(
            state = state,
            contentPadding = contentPadding,
            horizontalArrangement = horizontalArrangement,
            content = content,
        )
    }
}

// ---------------------------------------------------------------- Rechte Side-Sheets

@Composable
private fun SideSheetContainer(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .padding(top = 24.dp, bottom = 24.dp, end = 24.dp)
            .width(356.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(26.dp))
            .background(Palette.SheetSurface)
            .border(1.dp, Palette.GlassEdge, RoundedCornerShape(26.dp))
            .padding(horizontal = 22.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
        content = content,
    )
}

@Composable
private fun AppSideSheet(
    app: AppEntry,
    vm: LauncherViewModel,
    onStartMove: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val favs by vm.favorites.collectAsState()
    val idx = favs.indexOf(app.pkg)
    val first = remember { FocusRequester() }
    val trapHorizontal = Modifier.focusProperties {
        left = FocusRequester.Cancel
        right = FocusRequester.Cancel
    }

    LaunchedEffect(app.pkg) {
        delay(60)
        runCatching { first.requestFocus() }
    }

    SideSheetContainer {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val pic = app.banner ?: app.icon
            if (pic != null) {
                Image(
                    pic, null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(if (app.banner != null) 78.dp else 44.dp, 44.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    app.label,
                    style = AiryText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = Palette.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (idx >= 0) "Favorit ${idx + 1} von ${favs.size}" else "App",
                    style = AiryText,
                    fontSize = 13.sp,
                    color = Palette.TextSecondary,
                )
            }
        }
        Spacer(Modifier.height(6.dp))

        TvButton(
            text = if (idx >= 0) "Aus Favoriten entfernen" else "Zu Favoriten hinzufügen",
            icon = TvIcons.Star,
            modifier = Modifier.fillMaxWidth().focusRequester(first).then(trapHorizontal),
        ) {
            vm.toggleFavorite(app.pkg)
            onDismiss()
        }
        if (idx >= 0 && favs.size > 1) {
            TvButton(
                text = "Im Dock verschieben",
                icon = TvIcons.MoveHorizontal,
                modifier = Modifier.fillMaxWidth().then(trapHorizontal),
            ) { onStartMove(app.pkg) }
        }
        TvButton(
            text = "Öffnen",
            icon = TvIcons.Play,
            modifier = Modifier.fillMaxWidth().then(trapHorizontal),
        ) {
            onDismiss()
            vm.launchApp(ctx, app)
        }
        TvButton(
            text = "App ausblenden",
            icon = TvIcons.EyeOff,
            modifier = Modifier.fillMaxWidth().then(trapHorizontal),
        ) {
            vm.hideApp(app.pkg)
            onDismiss()
        }
        TvButton(
            text = "App-Info",
            icon = TvIcons.Info,
            modifier = Modifier.fillMaxWidth().then(trapHorizontal),
        ) {
            onDismiss()
            startSafely(
                ctx,
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        TvButton(
            text = "Deinstallieren",
            icon = TvIcons.Trash,
            destructive = true,
            modifier = Modifier.fillMaxWidth().then(trapHorizontal),
        ) {
            onDismiss()
            startSafely(
                ctx,
                Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.pkg}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }

        Spacer(Modifier.weight(1f))

        TvButton(
            text = "Launcher-Einstellungen",
            icon = TvIcons.Settings,
            modifier = Modifier.fillMaxWidth().then(trapHorizontal),
        ) { onOpenSettings() }
    }
}

@Composable
private fun ShelfSideSheet(
    item: JfItem,
    vm: LauncherViewModel,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val first = remember { FocusRequester() }
    val trapHorizontal = Modifier.focusProperties {
        left = FocusRequester.Cancel
        right = FocusRequester.Cancel
    }

    LaunchedEffect(item.id) {
        delay(60)
        runCatching { first.requestFocus() }
    }

    SideSheetContainer {
        Text(
            heroTitle(item),
            style = AiryText,
            fontSize = 19.sp,
            fontWeight = FontWeight.Medium,
            color = Palette.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val sub = listOfNotNull(
            item.subtitle.takeIf { it.isNotBlank() && it != heroTitle(item) },
            item.remaining,
        ).joinToString(" · ")
        if (sub.isNotEmpty()) {
            Text(sub, style = AiryText, fontSize = 13.5.sp, color = Palette.TextSecondary, maxLines = 1)
        }
        Spacer(Modifier.height(8.dp))

        TvButton(
            text = "In Jellyfin öffnen",
            icon = TvIcons.Play,
            modifier = Modifier.fillMaxWidth().focusRequester(first).then(trapHorizontal),
        ) {
            onDismiss()
            vm.openInJellyfin(ctx, item)
        }
        TvButton(
            text = "Als gesehen markieren",
            icon = TvIcons.Check,
            modifier = Modifier.fillMaxWidth().then(trapHorizontal),
        ) {
            vm.markPlayed(ctx, item)
            onDismiss()
        }
        TvButton(
            text = "Inhalte aktualisieren",
            icon = TvIcons.Refresh,
            modifier = Modifier.fillMaxWidth().then(trapHorizontal),
        ) {
            vm.refreshJellyfin()
            onDismiss()
        }

        Spacer(Modifier.weight(1f))

        TvButton(
            text = "Launcher-Einstellungen",
            icon = TvIcons.Settings,
            modifier = Modifier.fillMaxWidth().then(trapHorizontal),
        ) { onOpenSettings() }
    }
}

@Composable
private fun HdmiSideSheet(
    inputs: List<TvInputEntry>,
    onSelect: (TvInputEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    val trapHorizontal = Modifier.focusProperties {
        left = FocusRequester.Cancel
        right = FocusRequester.Cancel
    }

    LaunchedEffect(Unit) {
        delay(60)
        runCatching { first.requestFocus() }
    }

    SideSheetContainer {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(TvIcons.TvInput, null, tint = Palette.TextPrimary, modifier = Modifier.size(22.dp))
            Text(
                "Eingänge",
                style = AiryText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = Palette.TextPrimary,
            )
        }
        Spacer(Modifier.height(6.dp))

        if (inputs.isEmpty()) {
            Text(
                "Keine HDMI-Eingänge gefunden.",
                style = AiryText,
                fontSize = 14.sp,
                color = Palette.TextSecondary,
            )
            Spacer(Modifier.height(8.dp))
            TvButton(
                text = "Schließen",
                modifier = Modifier.fillMaxWidth().focusRequester(first).then(trapHorizontal),
                onClick = onDismiss,
            )
        } else {
            inputs.forEachIndexed { idx, input ->
                TvButton(
                    text = input.label,
                    icon = TvIcons.TvInput,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (idx == 0) Modifier.focusRequester(first) else Modifier)
                        .then(trapHorizontal),
                    onClick = { onSelect(input) },
                )
            }
        }
    }
}
