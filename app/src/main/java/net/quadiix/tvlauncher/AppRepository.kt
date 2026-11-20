package net.quadiix.tvlauncher

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/** Liest alle startbaren Apps (TV-Apps zuerst, dann normale Android-Apps). */
object AppRepository {

    fun load(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val leanback = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0,
        )
        val normal = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0,
        )

        val seen = HashSet<String>()
        val result = ArrayList<AppEntry>()

        fun add(ri: ResolveInfo, isLeanback: Boolean) {
            val ai = ri.activityInfo
            val pkg = ai.packageName
            if (pkg == context.packageName || !seen.add(pkg)) return

            val intent = Intent(Intent.ACTION_MAIN)
                .addCategory(if (isLeanback) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER)
                .setClassName(pkg, ai.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

            val bannerBmp = runCatching {
                (ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm))?.toBitmap(320, 180)
            }.getOrNull()
            val iconBmp = if (bannerBmp == null) {
                runCatching { ri.loadIcon(pm).toBitmap(192, 192) }.getOrNull()
            } else null
            // Mini-Fassung für den weichgezeichneten Ambient-Hintergrund (einmalig hier im IO-Thread)
            val ambient = (bannerBmp ?: iconBmp)?.let { runCatching { Images.soften(it) }.getOrNull() }
            val cardTint = iconBmp?.let { runCatching { extractCardTint(it) }.getOrNull() }

            result += AppEntry(
                pkg = pkg,
                label = ri.loadLabel(pm).toString(),
                banner = bannerBmp?.asImageBitmap(),
                icon = iconBmp?.asImageBitmap(),
                launchIntent = intent,
                ambient = ambient,
                cardTint = cardTint,
            )
        }

        leanback.forEach { add(it, true) }
        normal.forEach { add(it, false) }
        return result.sortedBy { it.label.lowercase() }
    }

    /**
     * Berechnet aus einem App-Icon eine ruhige, dunkle Kartenfarbe für Apps ohne TV-Banner.
     * Läuft einmalig im IO-Thread → null GPU-Kosten zur Laufzeit.
     */
    private fun extractCardTint(bmp: Bitmap): Int {
        val small = Bitmap.createScaledBitmap(bmp, 16, 16, true)
        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var count = 0L
        val pixels = IntArray(256)
        small.getPixels(pixels, 0, 16, 0, 0, 16, 16)
        for (px in pixels) {
            val a = AndroidColor.alpha(px)
            if (a < 128) continue
            rSum += AndroidColor.red(px)
            gSum += AndroidColor.green(px)
            bSum += AndroidColor.blue(px)
            count++
        }
        if (count == 0L) return 0xFF1E1E22.toInt()
        val avgR = (rSum / count).toFloat()
        val avgG = (gSum / count).toFloat()
        val avgB = (bSum / count).toFloat()
        // Ruhig mit dunklem Anthrazit (#1C1C20) mischen, damit kein schreiender Farbblock entsteht
        val r = (0x1C * 0.72f + avgR * 0.28f).toInt().coerceIn(22, 72)
        val g = (0x1C * 0.72f + avgG * 0.28f).toInt().coerceIn(22, 72)
        val b = (0x20 * 0.72f + avgB * 0.28f).toInt().coerceIn(24, 78)
        return AndroidColor.rgb(r, g, b)
    }
}
