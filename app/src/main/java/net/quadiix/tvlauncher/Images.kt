package net.quadiix.tvlauncher

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Gemeinsame Bild-Anfragen. Anzeige und Vorladen MÜSSEN dieselben Anfragen benutzen (gleiche Größe),
 * sonst landen sie unter verschiedenen Schlüsseln im Speicher-Cache und das Vorladen nützt nichts.
 */
object Images {

    /** Großes Hintergrundbild: 1280x720 in RGB_565 reicht unter der Abdunklung völlig. */
    fun backdrop(context: Context, url: String): ImageRequest =
        ImageRequest.Builder(context)
            .data(url)
            .size(1280, 720)
            .bitmapConfig(Bitmap.Config.RGB_565)
            .build()

    /** Querformat-Kachel in der Shelf (224x126 dp ≈ 448x252 px, mit Vergrößerung etwas mehr). */
    fun card(context: Context, url: String): ImageRequest =
        ImageRequest.Builder(context).data(url).size(520, 292).build()

    /** Titel-Logo (transparent). */
    fun logo(context: Context, url: String): ImageRequest =
        ImageRequest.Builder(context).data(url).size(600, 170).build()

    /**
     * Stark weichgezeichnete Fassung eines Bildes: auf 24x14 Pixel verkleinert und auf 96x54 geglättet.
     * Groß aufgezogen wirkt das wie ein Gaussian Blur > 80 dp – kostet aber nur so viel wie ein Bild,
     * während echte Echtzeit-Unschärfe (RenderEffect) die TV-GPU überfordert.
     */
    suspend fun blurred(context: Context, url: String): ImageBitmap? = withContext(Dispatchers.IO) {
        val req = ImageRequest.Builder(context)
            .data(url)
            .size(48, 27)
            .allowHardware(false)
            .build()
        val result = SingletonImageLoader.get(context).execute(req) as? SuccessResult ?: return@withContext null
        soften(result.image.toBitmap())
    }

    /**
     * Verkleinern + wieder leicht vergrößern mit Filter = sehr weicher Verlauf ohne Kanten.
     * Zwei Verkleinerungsstufen, damit auch bei hellen Logos nur noch die Farbstimmung übrig bleibt
     * (in 10x6 Pixeln sind keine Formen mehr erkennbar).
     */
    fun soften(src: Bitmap): ImageBitmap {
        val mid = Bitmap.createScaledBitmap(src, 40, 23, true)
        val tiny = Bitmap.createScaledBitmap(mid, 10, 6, true)
        return Bitmap.createScaledBitmap(tiny, 80, 45, true).asImageBitmap()
    }

    /**
     * Entsättigen + Abdunkeln einmalig in die (winzige) Bitmap einrechnen. Als Farbfilter beim Zeichnen
     * würde die GPU das bei jedem Bild für alle ~2 Mio. Bildschirmpixel neu rechnen.
     */
    fun tone(src: ImageBitmap, saturation: Float, brightness: Float): ImageBitmap {
        val bmp = src.asAndroidBitmap()
        val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val m = android.graphics.ColorMatrix().apply {
            setSaturation(saturation)
            postConcat(android.graphics.ColorMatrix().apply { setScale(brightness, brightness, brightness, 1f) })
        }
        val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = android.graphics.ColorMatrixColorFilter(m)
        }
        android.graphics.Canvas(out).drawBitmap(bmp, 0f, 0f, paint)
        return out.asImageBitmap()
    }

    /** Lädt alle Bilder der Shelf vorab in den Cache (läuft im Hintergrund). */
    fun preload(context: Context, items: List<JfItem>) {
        val loader = SingletonImageLoader.get(context)
        items.forEach { item ->
            item.imageUrl?.let { loader.enqueue(card(context, it)) }
            item.logoUrl?.let { loader.enqueue(logo(context, it)) }
        }
        // Hintergründe nur für die ersten Titel – die sind groß
        items.take(6).forEach { item -> item.backdropUrl?.let { loader.enqueue(backdrop(context, it)) } }
    }
}
