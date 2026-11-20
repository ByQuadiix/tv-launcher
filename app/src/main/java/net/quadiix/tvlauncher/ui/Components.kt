package net.quadiix.tvlauncher.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------- Farben & Schrift (tvOS / Mini-LED)

/** Ruhige Graustufen statt hartem Schwarz-Weiß – vermeidet Blooming auf dem Mini-LED-Panel. */
object Palette {
    val Background = Color(0xFF121214)
    val Surface = Color(0xFF1C1C20)
    val SheetSurface = Color(0xEE18181C)
    val TextPrimary = Color.White.copy(alpha = 0.90f)
    val TextSecondary = Color.White.copy(alpha = 0.65f)
    val TextMuted = Color.White.copy(alpha = 0.45f)
    val Glass = Color.White.copy(alpha = 0.08f)
    val GlassEdge = Color.White.copy(alpha = 0.14f)
    val FocusRing = Color.White.copy(alpha = 0.72f)
}

/** Leicht erhöhter Zeichenabstand (0,02 em), klar lesbar aus 3 m Entfernung. */
val AiryText = TextStyle(letterSpacing = 0.02.em)

/** Nicht ausgewählte App-Banner leicht abdunkeln, damit grelle Logos die Ruhe nicht stören. */
val UnfocusedDim: ColorFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            0.78f, 0f, 0f, 0f, 0f,
            0f, 0.78f, 0f, 0f, 0f,
            0f, 0f, 0.78f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    ).apply { timesAssign(ColorMatrix().apply { setToSaturation(0.85f) }) }
)

/** Gefederte Fokus-Animation ohne Nachschwingen (NoBouncy, MediumLow). */
val FocusSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

// ---------------------------------------------------------------- Fernbedienung

/**
 * OK-Taste der Fernbedienung: kurz drücken = [onClick], gedrückt halten (oder Menü-Taste) = [onLongClick].
 * Ein Loslassen ohne vorheriges Drücken auf demselben Element wird ignoriert, damit ein Tastendruck
 * nicht versehentlich in einem gerade geöffneten Menü „durchklickt“.
 */
fun Modifier.dpadClickable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
): Modifier = composed {
    val click by rememberUpdatedState(onClick)
    val longClick by rememberUpdatedState(onLongClick)
    var downSeen by remember { mutableStateOf(false) }
    var longFired by remember { mutableStateOf(false) }

    onKeyEvent { ev ->
        val native = ev.nativeKeyEvent
        when (native.keyCode) {
            AndroidKeyEvent.KEYCODE_DPAD_CENTER,
            AndroidKeyEvent.KEYCODE_ENTER,
            AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> {
                when (ev.type) {
                    KeyEventType.KeyDown -> {
                        if (native.repeatCount == 0) {
                            downSeen = true
                            longFired = false
                        } else if (downSeen && !longFired && longClick != null) {
                            longFired = true
                            longClick?.invoke()
                        }
                        true
                    }
                    KeyEventType.KeyUp -> {
                        if (downSeen && !longFired) click()
                        downSeen = false
                        longFired = false
                        true
                    }
                    else -> false
                }
            }
            AndroidKeyEvent.KEYCODE_MENU -> {
                val l = longClick ?: return@onKeyEvent false
                if (ev.type == KeyEventType.KeyUp) l()
                true
            }
            else -> false
        }
    }
}

private class BoundsHolder {
    var rect: Rect = Rect.Zero
}

// ---------------------------------------------------------------- Kachel

/**
 * Kachel im Apple-TV-Stil: gefederte Vergrößerung (1,08×), weicher Schatten und klar erkennbarer
 * Lichtrand (auf hellen wie dunklen Bannern aus 3 m sofort sichtbar).
 * [bounds] wird bewusst ohne Compose-State gespeichert, damit Scrollen keine Rekompositionen auslöst.
 */
@Composable
fun FocusCard(
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    onClick: (bounds: Rect) -> Unit,
    onLongClick: (() -> Unit)? = null,
    onFocused: () -> Unit = {},
    onFocusChange: (Boolean) -> Unit = {},
    corner: Dp = 18.dp,
    focusScale: Float = 1.08f,
    isMoving: Boolean = false,
    background: Color = Palette.Surface,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val bounds = remember { BoundsHolder() }
    val targetScale = if (isMoving) focusScale + 0.03f else focusScale
    val progress by animateFloatAsState(if (focused || isMoving) 1f else 0f, FocusSpring, label = "focus")
    val cardShape = remember(corner) { RoundedCornerShape(corner) }
    val elevationPx = with(LocalDensity.current) { 24.dp.toPx() }

    Box(
        modifier
            .size(width, height)
            .onGloballyPositioned { bounds.rect = it.boundsInRoot() }
            .graphicsLayer {
                val s = 1f + (targetScale - 1f) * progress
                scaleX = s
                scaleY = s
                shadowElevation = elevationPx * progress
                ambientShadowColor = Color.Black
                spotShadowColor = Color.Black
                shape = cardShape
                clip = true
            }
            .then(if (background.alpha > 0f) Modifier.background(background) else Modifier)
            .dpadClickable({ onClick(bounds.rect) }, onLongClick)
            .onFocusChanged {
                if (focused != it.isFocused) onFocusChange(it.isFocused)
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .focusable(),
    ) {
        content(focused || isMoving)
        if (progress > 0.02f) {
            val borderColor = if (isMoving) {
                Color.White.copy(alpha = 0.92f)
            } else {
                Palette.FocusRing.copy(alpha = 0.72f * progress)
            }
            Box(
                Modifier
                    .matchParentSize()
                    .border(if (isMoving) 2.5.dp else 2.dp, borderColor, cardShape)
            )
        }
    }
}

// ---------------------------------------------------------------- Top-Bar Icon-Button

/** Kompakter runder Glas-Knopf für die obere Leiste (z. B. Einstellungen, HDMI). */
@Composable
fun TvIconButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(if (focused) 1f else 0f, FocusSpring, label = "iconBtn")

    Box(
        modifier
            .size(38.dp)
            .graphicsLayer {
                val s = 1f + 0.12f * progress
                scaleX = s
                scaleY = s
            }
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.06f + 0.18f * progress))
            .border(1.5.dp, Color.White.copy(alpha = 0.10f + 0.60f * progress), CircleShape)
            .dpadClickable(onClick)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = 0.62f + 0.34f * progress),
            modifier = Modifier.size(19.dp),
        )
    }
}

// ---------------------------------------------------------------- Kategorie-Pill (Top Shelf)

/** Apple-TV-artige Segment-Pill für Jellyfin-Kategorien (Weitergucken / Als Nächstes / Neu). */
@Composable
fun TvCategoryPill(
    text: String,
    active: Boolean,
    dimmed: Boolean = false,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(if (focused) 1f else 0f, FocusSpring, label = "pill")
    val shape = remember { RoundedCornerShape(50) }

    val bgAlpha = when {
        focused -> 0.24f
        active -> 0.14f
        else -> 0.04f
    }
    val textAlpha = when {
        focused -> 0.96f
        active -> 0.90f
        dimmed -> 0.36f
        else -> 0.60f
    }

    Box(
        modifier
            .graphicsLayer {
                val s = 1f + 0.05f * progress
                scaleX = s
                scaleY = s
            }
            .clip(shape)
            .background(Color.White.copy(alpha = bgAlpha))
            .border(
                width = if (focused) 1.5.dp else 1.dp,
                color = Color.White.copy(alpha = if (focused) 0.72f else if (active) 0.18f else 0.05f),
                shape = shape,
            )
            .dpadClickable(onClick)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .focusable()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = AiryText,
            fontSize = 14.sp,
            fontWeight = if (active || focused) FontWeight.Medium else FontWeight.Normal,
            color = Color.White.copy(alpha = textAlpha),
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------- Listenzeilen

/**
 * Listenzeile für Menüs und Einstellungen mit optionalem Vektor-Icon, Wert und Auswahl-Häkchen.
 */
@Composable
fun TvButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selected: Boolean = false,
    active: Boolean = false,
    value: String? = null,
    destructive: Boolean = false,
    onFocused: () -> Unit = {},
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(if (focused) 1f else 0f, FocusSpring, label = "btn")
    val shape = remember { RoundedCornerShape(14.dp) }
    val baseFg = if (destructive) Color(0xFFFF7A7A) else Color.White
    val fg = baseFg.copy(alpha = (if (active) 0.92f else 0.78f) + 0.18f * progress)
    val bgAlpha = (if (active) 0.12f else 0.05f) + 0.15f * progress

    Row(
        modifier
            .graphicsLayer {
                val s = 1f + 0.025f * progress
                scaleX = s
                scaleY = s
            }
            .clip(shape)
            .background(Color.White.copy(alpha = bgAlpha))
            .border(
                width = if (focused) 1.5.dp else 1.dp,
                color = Color.White.copy(alpha = if (focused) 0.58f else if (active) 0.14f else 0.04f),
                shape = shape,
            )
            .dpadClickable(onClick)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .focusable()
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(19.dp),
            )
            Spacer(Modifier.width(13.dp))
        }
        Text(
            text = text,
            style = AiryText,
            color = fg,
            fontSize = 15.5.sp,
            fontWeight = if (focused || active) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                value,
                style = AiryText,
                color = Palette.TextSecondary,
                fontSize = 14.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Spacer(Modifier.width(10.dp))
            Icon(
                imageVector = TvIcons.Check,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Listenzeile mit sauberem Ein/Aus-Schalter für die Einstellungen. */
@Composable
fun TvToggleButton(
    text: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onToggle: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(if (focused) 1f else 0f, FocusSpring, label = "toggle")
    val knob by animateFloatAsState(if (checked) 1f else 0f, FocusSpring, label = "knob")
    val shape = remember { RoundedCornerShape(14.dp) }
    val fg = Color.White.copy(alpha = 0.80f + 0.16f * progress)

    Row(
        modifier
            .graphicsLayer {
                val s = 1f + 0.025f * progress
                scaleX = s
                scaleY = s
            }
            .clip(shape)
            .background(Color.White.copy(alpha = 0.05f + 0.15f * progress))
            .border(
                width = if (focused) 1.5.dp else 1.dp,
                color = Color.White.copy(alpha = if (focused) 0.58f else 0.04f),
                shape = shape,
            )
            .dpadClickable(onToggle)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(
                text = text,
                style = AiryText,
                color = fg,
                fontSize = 15.5.sp,
                fontWeight = if (focused) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = AiryText,
                    color = Palette.TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Schlanke Apple-TV-Schalter-Pille
        val density = LocalDensity.current
        val travelPx = with(density) { 18.dp.toPx() }
        Box(
            Modifier
                .width(44.dp)
                .height(26.dp)
                .clip(CircleShape)
                .background(
                    if (checked) Color.White.copy(alpha = 0.82f)
                    else Color.White.copy(alpha = 0.16f)
                )
                .padding(3.dp),
        ) {
            Box(
                Modifier
                    .size(20.dp)
                    .graphicsLayer { translationX = travelPx * knob }
                    .clip(CircleShape)
                    .background(if (checked) Palette.Background else Color.White.copy(alpha = 0.85f))
            )
        }
    }
}
