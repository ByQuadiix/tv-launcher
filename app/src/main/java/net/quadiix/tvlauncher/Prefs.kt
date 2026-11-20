package net.quadiix.tvlauncher

import android.content.Context
import androidx.core.content.edit
import java.util.UUID

/** Alle Einstellungen des Launchers, lokal in den App-Daten gespeichert. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    var favorites: List<String>
        get() = (sp.getString("favorites", DEFAULT_FAVORITES) ?: "").split('|').filter { it.isNotBlank() }
        set(v) = sp.edit { putString("favorites", v.joinToString("|")) }

    var hiddenApps: Set<String>
        get() = (sp.getString("hidden_apps", "") ?: "").split('|').filter { it.isNotBlank() }.toSet()
        set(v) = sp.edit { putString("hidden_apps", v.joinToString("|")) }

    var hideFavoritesInAllApps: Boolean
        get() = sp.getBoolean("hide_favs_in_all_apps", true)
        set(v) = sp.edit { putBoolean("hide_favs_in_all_apps", v) }

    var showHdmiQuickAction: Boolean
        get() = sp.getBoolean("show_hdmi_quick_action", false)
        set(v) = sp.edit { putBoolean("show_hdmi_quick_action", v) }

    var jfServer: String
        get() = sp.getString("jf_server", DEFAULT_SERVER) ?: DEFAULT_SERVER
        set(v) = sp.edit { putString("jf_server", v) }

    var jfToken: String?
        get() = sp.getString("jf_token", null)
        set(v) = sp.edit { putString("jf_token", v) }

    var jfUserId: String?
        get() = sp.getString("jf_user_id", null)
        set(v) = sp.edit { putString("jf_user_id", v) }

    var jfUserName: String?
        get() = sp.getString("jf_user_name", null)
        set(v) = sp.edit { putString("jf_user_name", v) }

    /** Feste Geräte-ID, damit Jellyfin den Launcher als ein Gerät wiedererkennt. */
    val deviceId: String
        get() = sp.getString("device_id", null) ?: UUID.randomUUID().toString().also {
            sp.edit { putString("device_id", it) }
        }

    /** "jellyfin" = Hintergrund des ausgewählten Titels, "gradient" = Farbverlauf, "custom" = eigenes Bild. */
    var wallpaperMode: String
        get() = sp.getString("wallpaper_mode", "jellyfin") ?: "jellyfin"
        set(v) = sp.edit { putString("wallpaper_mode", v) }

    var wallpaperUrl: String?
        get() = sp.getString("wallpaper_url", null)
        set(v) = sp.edit { putString("wallpaper_url", v) }

    companion object {
        const val DEFAULT_SERVER = "http://192.168.2.200:8096"
        const val DEFAULT_FAVORITES = "org.jellyfin.androidtv"
    }
}
