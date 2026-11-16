package net.quadiix.tvlauncher

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.allowRgb565
import coil3.request.crossfade

class LauncherApp : Application(), SingletonImageLoader.Factory {

    /**
     * Bilder ohne Transparenz (Fotos, Poster) als RGB_565 laden: halbe Datenmenge pro Pixel.
     * Auf dem TV ist die Speicherbandbreite der GPU knapp; Logos mit Transparenz bleiben ARGB.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .allowRgb565(true)
            .crossfade(false)
            .build()
}
