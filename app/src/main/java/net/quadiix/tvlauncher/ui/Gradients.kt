package net.quadiix.tvlauncher.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale

/**
 * Vorgerechnete Verläufe als winzige Bitmaps.
 *
 * Gemessen auf dem Xiaomi-TV (MediaTek MT5896): Ein Compose-Verlauf (Brush) über den ganzen Bildschirm
 * kostet die GPU MEHR als ein Foto, weil Skia jeden Pixel mit Dithering neu berechnet. Eine kleine
 * Bitmap, die nur gestreckt gezeichnet wird, ist dagegen so billig wie jedes andere Bild.
 */
object Gradients {

    /** Von oben transparent nach unten dunkel – für den Text unten in Karten. */
    val bottomScrim: ImageBitmap by lazy { vertical(intArrayOf(0x00000000, 0xD9000000.toInt())) }

    /**
     * Weicher, elliptischer Schatten hinter dem Hero-Text: am linken Rand auf Texthöhe am dunkelsten,
     * nach oben, unten und rechts läuft er ohne sichtbare Kante aus (kein „dunkler Kasten“).
     * Mittelpunkt liegt bei x = 0 und y = 62 % der Bitmap-Höhe.
     */
    val heroScrim: ImageBitmap by lazy {
        val w = 128
        val h = 72
        val pixels = IntArray(w * h)
        val maxAlpha = 0.62f
        for (y in 0 until h) {
            for (x in 0 until w) {
                val dx = x / (w * 0.95f)
                val dy = (y - h * 0.62f) / (h * 0.50f)
                val d = kotlin.math.sqrt(dx * dx + dy * dy)
                // smoothstep(0.30, 1.0, d) -> 0 innen, 1 außen
                val t = ((d - 0.30f) / 0.70f).coerceIn(0f, 1f)
                val fade = 1f - t * t * (3f - 2f * t)
                val a = (maxAlpha * fade * 255f).toInt().coerceIn(0, 255)
                pixels[y * w + x] = a shl 24
            }
        }
        Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    /** Ruhiger dunkler Hintergrund, wenn kein Filmbild da ist. */
    val wallpaper: ImageBitmap by lazy {
        val w = 64
        val h = 36
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                intArrayOf(0xFF1C1F26.toInt(), 0xFF2B3038.toInt(), 0xFF15171C.toInt()),
                null, Shader.TileMode.CLAMP,
            )
            isDither = true
        }
        Canvas(bmp).drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        bmp.asImageBitmap()
    }

    private fun vertical(colors: IntArray): ImageBitmap {
        val bmp = Bitmap.createBitmap(1, 128, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, 128f, colors, null, Shader.TileMode.CLAMP)
        }
        Canvas(bmp).drawRect(0f, 0f, 1f, 128f, paint)
        return bmp.asImageBitmap()
    }

}

/** Zeichnet eine der vorgerechneten Verlaufs-Bitmaps gestreckt auf die Fläche von [modifier]. */
@Composable
fun GradientImage(bitmap: ImageBitmap, modifier: Modifier) {
    Image(
        painter = BitmapPainter(bitmap),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = modifier,
    )
}
