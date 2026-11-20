package net.quadiix.tvlauncher

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager

/** Liest verfügbare HDMI-/Hardware-Eingänge über den Android-TV-Input-Dienst. */
object TvInputs {

    fun load(context: Context): List<TvInputEntry> {
        val mgr = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager ?: return emptyList()
        val list = runCatching { mgr.tvInputList }.getOrNull() ?: return emptyList()
        return list
            .filter { info ->
                info.isPassthroughInput && (
                    info.type == TvInputInfo.TYPE_HDMI ||
                        info.type == TvInputInfo.TYPE_COMPONENT ||
                        info.type == TvInputInfo.TYPE_COMPOSITE ||
                        info.type == TvInputInfo.TYPE_DISPLAY_PORT
                    )
            }
            .map { info ->
                val custom = runCatching { info.loadCustomLabel(context)?.toString() }.getOrNull()
                val label = custom?.takeIf { it.isNotBlank() }
                    ?: runCatching { info.loadLabel(context).toString() }.getOrNull()?.takeIf { it.isNotBlank() }
                    ?: "HDMI"
                TvInputEntry(id = info.id, label = label)
            }
            .sortedBy { it.label.lowercase() }
    }

    fun open(context: Context, input: TvInputEntry) {
        val uri = TvContract.buildChannelUriForPassthroughInput(input.id)
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startSafely(context, intent)
    }
}
