package net.quadiix.tvlauncher

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/** Liest alle startbaren Apps (TV-Apps zuerst, dann normale Android-Apps). */
object AppRepository {

    fun load(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val leanback = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0
        )
        val normal = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
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

            val banner = runCatching {
                (ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm))
                    ?.toBitmap(320, 180)?.asImageBitmap()
            }.getOrNull()
            val icon = if (banner == null) {
                runCatching { ri.loadIcon(pm).toBitmap(192, 192).asImageBitmap() }.getOrNull()
            } else null

            result += AppEntry(pkg, ri.loadLabel(pm).toString(), banner, icon, intent)
        }

        leanback.forEach { add(it, true) }
        normal.forEach { add(it, false) }
        return result.sortedBy { it.label.lowercase() }
    }
}
