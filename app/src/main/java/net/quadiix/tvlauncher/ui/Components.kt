package net.quadiix.tvlauncher.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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

// ---------------------------------------------------------------- Farben & Schrift (tvOS-artig)

/** Ruhige Graustufen statt hartem Schwarz-Weiß. */
object Palette {
    val Background = Color(0xFF121214)
    val Surface = Color(0xFF1C1C1E)
    val TextPrimary = Color.White.copy(alpha = 0.85f)
    val TextSecondary = Color.White.copy(alpha = 0.60f)
    val Glass = Color.White.copy(alpha = 0.08f)
    val GlassEdge = Color.White.copy(alpha = 0.10f)
}

/** Leicht erhöhter Zeichenabstand (0,02 em), keine schweren Fettschnitte. */
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

/** Gefederte Fokus-Animation ohne Nachschwingen (Spezifikation: NoBouncy, MediumLow). */
private val FocusSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

// ---------------------------------------------------------------- Fernbedienung

/**
 * OK-Taste der Fernbedienung: kurz drücken = [onClick], gedrückt halten (oder Menü-Taste) = [onLongClick].
 * Ein Loslassen ohne vorheriges Drücken auf demselben Element wird ignoriert, damit ein Tastendruck
 * nicht versehentlich in einem gerade geöffneten Dialog „durchklickt“.
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

// ---------------------------------------------------------------- Kachel

/**
 * Kachel im tvOS-Stil: Fokus nur über sanfte Vergrößerung (Feder), einen weichen Schlagschatten und
 * einen feinen hellen Innenrand – kein harter Rahmen. [onClick] bekommt die Position der Kachel auf dem
 * Bildschirm, damit eine App aus der Kachel heraus „aufgezoomt“ werden kann.
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
    focusScale: Float = 1.07f,
    background: Color = Palette.Surface,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val progress by animateFloatAsState(if (focused) 1f else 0f, FocusSpring, label = "focus")
    val cardShape = remember(corner) { RoundedCornerShape(corner) }
    val elevationPx = with(LocalDensity.current) { 22.dp.toPx() }

    Box(
        modifier
            .size(width, height)
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            // Alles über die GPU-Ebene: Skalierung und Schatten. Der Schatten entsteht nur an der
            // ausgewählten Kachel (Elevation 0 = kein Schatten), damit die TV-GPU nicht überlastet wird.
            .graphicsLayer {
                val s = 1f + (focusScale - 1f) * progress
                scaleX = s
                scaleY = s
                shadowElevation = elevationPx * progress
                ambientShadowColor = Color.Black
                spotShadowColor = Color.Black
                shape = cardShape
                clip = true
            }
            .then(if (background.alpha > 0f) Modifier.background(background) else Modifier)
            .dpadClickable({ onClick(bounds) }, onLongClick)
            .onFocusChanged {
                if (focused != it.isFocused) onFocusChange(it.isFocused)
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .focusable(),
    ) {
        content(focused)
        // Feiner heller Innenrand (Specular Highlight) – nur sichtbar, solange die Kachel ausgewählt ist
        if (progress > 0.02f) {
            Box(
                Modifier.matchParentSize()
                    .border(1.5.dp, Color.White.copy(alpha = 0.18f * progress), cardShape)
            )
        }
    }
}

// ---------------------------------------------------------------- Listenzeile

/**
 * Listenzeile für Menüs und Einstellungen. Ausgewählt: etwas heller und leicht vergrößert –
 * bewusst ohne harten Weiß-Schwarz-Kontrast. [value] steht rechtsbündig, [selected] zeigt ein Häkchen.
 */
@Composable
fun TvButton(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    value: String? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(if (focused) 1f else 0f, FocusSpring, label = "btn")
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .graphicsLayer {
                val s = 1f + 0.03f * progress
                scaleX = s
                scaleY = s
            }
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f + 0.14f * progress))
            .border(1.dp, Color.White.copy(alpha = 0.16f * progress), shape)
            .dpadClickable(onClick)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = Color.White.copy(alpha = 0.78f + 0.17f * progress)
        Text(
            text = text,
            style = AiryText,
            color = fg,
            fontSize = 16.sp,
            fontWeight = if (focused) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Text(value, style = AiryText, color = Palette.TextSecondary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (selected) {
            Text("✓", color = fg, fontSize = 16.sp, modifier = Modifier.padding(start = 12.dp))
        }
    }
}
