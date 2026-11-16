package net.quadiix.tvlauncher

import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap

data class AppEntry(
    val pkg: String,
    val label: String,
    /** 16:9-Banner der App (Android-TV-Apps haben eins), sonst null. */
    val banner: ImageBitmap?,
    /** Normales App-Icon als Ersatz, wenn kein Banner existiert. */
    val icon: ImageBitmap?,
    val launchIntent: Intent,
)

data class JfItem(
    val id: String,
    val title: String,
    val subtitle: String,
    /** Querformat-Bild für die Kachel. */
    val imageUrl: String?,
    /** Großes Hintergrundbild für den Bildschirmhintergrund. */
    val backdropUrl: String?,
    /** Fortschritt 0..1 für "Weiterschauen", sonst null. */
    val progress: Float?,
    /** true bei Bibliotheken (Filme, Serien …). */
    val isView: Boolean = false,
    /** Transparentes Titel-Logo (Film/Serie), falls auf dem Server vorhanden. */
    val logoUrl: String? = null,
    /** Hochformat-Poster (2:3). */
    val posterUrl: String? = null,
    /** Kurzbeschreibung für den großen Bereich oben. */
    val overview: String? = null,
    /** Infozeile, z. B. "2017 · FSK 12 · 2 Std 4 Min · ★ 6,1". */
    val meta: String = "",
    /** Restlaufzeit bei angefangenen Titeln, z. B. "Noch 58 Min". */
    val remaining: String? = null,
    /** true, wenn im Kachelbild der Titel schon eingebaut ist (Jellyfin-"Thumb") – dann kein Logo darüberlegen. */
    val imageHasTitle: Boolean = false,
)

data class Weather(
    val temp: Int,
    val min: Int,
    val max: Int,
    val code: Int,
    val isDay: Boolean,
    val city: String,
)
