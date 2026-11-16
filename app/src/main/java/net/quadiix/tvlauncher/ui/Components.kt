package net.quadiix.tvlauncher.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


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

/** Kachel, die beim Fokussieren größer wird und einen weißen Rahmen bekommt. */
@Composable
fun FocusCard(
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onFocused: () -> Unit = {},
    onFocusChange: (Boolean) -> Unit = {},
    corner: Dp = 14.dp,
    focusScale: Float = 1.1f,
    background: Color = Color(0xFF2A2A30),
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) focusScale else 1f, tween(140), label = "cardScale")
    val cardShape = remember(corner) { RoundedCornerShape(corner) }
    Box(
        modifier
            .size(width, height)
            // Nur Skalieren über die GPU-Ebene; der frühere Schatten wurde bei jedem Animationsschritt
            // neu berechnet und war auf der schwachen TV-GPU teuer.
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.shape = cardShape
                clip = true
            }
            // Fläche und Rahmen nur zeichnen, wenn sie sichtbar sind – jede überflüssige Fläche kostet
            // auf der TV-GPU messbar Zeit (Karten mit Bild bekommen Color.Transparent).
            .then(if (background.alpha > 0f) Modifier.background(background) else Modifier)
            .then(if (focused) Modifier.border(2.dp, Color.White.copy(alpha = 0.9f), cardShape) else Modifier)
            .dpadClickable(onClick, onLongClick)
            .onFocusChanged {
                if (focused != it.isFocused) onFocusChange(it.isFocused)
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .focusable(),
    ) {
        content(focused)
    }
}

/**
 * Listenzeile für Menüs und Einstellungen: ausgewählt weiß mit dunkler Schrift und leicht vergrößert.
 * [value] erscheint rechtsbündig (z. B. "An"), [selected] zeigt ein Häkchen.
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
    val scale by animateFloatAsState(if (focused) 1.03f else 1f, tween(120), label = "btnScale")
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.09f))
            .dpadClickable(onClick)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (focused) Color(0xFF111114) else Color.White
        Text(
            text = text,
            color = fg,
            fontSize = 16.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Text(value, color = fg.copy(alpha = 0.6f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (selected) {
            Text("✓", color = fg, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp))
        }
    }
}
