package net.quadiix.tvlauncher.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Leichtgewichtige Vektor-Icons (24×24 Viewport, klare Linien im Apple-TV-Stil).
 * Ersetzt Unicode-Symbole („⚙“) und spart die schwere Material-Icons-Extended-Bibliothek.
 */
object TvIcons {

    private val stroke = SolidColor(Color.White)

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply(block).build()

    /** Zahnrad für Einstellungen. */
    val Settings: ImageVector by lazy {
        icon("Settings") {
            path(
                stroke = stroke,
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Innerer Kreis
                moveTo(15f, 12f)
                arcTo(3f, 3f, 0f, true, true, 9f, 12f)
                arcTo(3f, 3f, 0f, true, true, 15f, 12f)
            }
            path(
                stroke = stroke,
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(19.4f, 15f)
                arcTo(1.65f, 1.65f, 0f, false, false, 19.73f, 16.82f)
                lineTo(19.79f, 16.88f)
                arcTo(2f, 2f, 0f, false, true, 16.96f, 19.71f)
                lineTo(16.9f, 19.65f)
                arcTo(1.65f, 1.65f, 0f, false, false, 15.08f, 19.32f)
                arcTo(1.65f, 1.65f, 0f, false, false, 14.08f, 20.83f)
                lineTo(14.08f, 21f)
                arcTo(2f, 2f, 0f, false, true, 10.08f, 21f)
                lineTo(10.08f, 20.91f)
                arcTo(1.65f, 1.65f, 0f, false, false, 9f, 19.4f)
                arcTo(1.65f, 1.65f, 0f, false, false, 7.18f, 19.73f)
                lineTo(7.12f, 19.79f)
                arcTo(2f, 2f, 0f, false, true, 4.29f, 16.96f)
                lineTo(4.35f, 16.9f)
                arcTo(1.65f, 1.65f, 0f, false, false, 4.68f, 15.08f)
                arcTo(1.65f, 1.65f, 0f, false, false, 3.17f, 14.08f)
                lineTo(3f, 14.08f)
                arcTo(2f, 2f, 0f, false, true, 3f, 10.08f)
                lineTo(3.09f, 10.08f)
                arcTo(1.65f, 1.65f, 0f, false, false, 4.6f, 9f)
                arcTo(1.65f, 1.65f, 0f, false, false, 4.27f, 7.18f)
                lineTo(4.21f, 7.12f)
                arcTo(2f, 2f, 0f, false, true, 7.04f, 4.29f)
                lineTo(7.1f, 4.35f)
                arcTo(1.65f, 1.65f, 0f, false, false, 8.92f, 4.68f)
                lineTo(9f, 4.68f)
                arcTo(1.65f, 1.65f, 0f, false, false, 10f, 3.17f)
                lineTo(10f, 3f)
                arcTo(2f, 2f, 0f, false, true, 14f, 3f)
                lineTo(14f, 3.09f)
                arcTo(1.65f, 1.65f, 0f, false, false, 15f, 4.6f)
                arcTo(1.65f, 1.65f, 0f, false, false, 16.82f, 4.27f)
                lineTo(16.88f, 4.21f)
                arcTo(2f, 2f, 0f, false, true, 19.71f, 7.04f)
                lineTo(19.65f, 7.1f)
                arcTo(1.65f, 1.65f, 0f, false, false, 19.32f, 8.92f)
                lineTo(19.32f, 9f)
                arcTo(1.65f, 1.65f, 0f, false, false, 20.83f, 10f)
                lineTo(21f, 10f)
                arcTo(2f, 2f, 0f, false, true, 21f, 14f)
                lineTo(20.91f, 14f)
                arcTo(1.65f, 1.65f, 0f, false, false, 19.4f, 15f)
                close()
            }
        }
    }

    /** TV / HDMI-Eingang. */
    val TvInput: ImageVector by lazy {
        icon("TvInput") {
            path(
                stroke = stroke,
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4f, 5f)
                lineTo(20f, 5f)
                arcTo(2f, 2f, 0f, false, true, 22f, 7f)
                lineTo(22f, 16f)
                arcTo(2f, 2f, 0f, false, true, 20f, 18f)
                lineTo(4f, 18f)
                arcTo(2f, 2f, 0f, false, true, 2f, 16f)
                lineTo(2f, 7f)
                arcTo(2f, 2f, 0f, false, true, 4f, 5f)
                close()
                moveTo(8f, 21f)
                lineTo(16f, 21f)
                moveTo(12f, 18f)
                lineTo(12f, 21f)
            }
            path(
                stroke = stroke,
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(10f, 9.5f)
                lineTo(14.5f, 11.5f)
                lineTo(10f, 13.5f)
                close()
            }
        }
    }

    /** Stern (Favorit). */
    val Star: ImageVector by lazy {
        icon("Star") {
            path(
                stroke = stroke,
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 3f)
                lineTo(14.8f, 8.7f)
                lineTo(21f, 9.6f)
                lineTo(16.5f, 14f)
                lineTo(17.6f, 20.2f)
                lineTo(12f, 17.3f)
                lineTo(6.4f, 20.2f)
                lineTo(7.5f, 14f)
                lineTo(3f, 9.6f)
                lineTo(9.2f, 8.7f)
                close()
            }
        }
    }

    /** Waagerecht verschieben. */
    val MoveHorizontal: ImageVector by lazy {
        icon("MoveHorizontal") {
            path(
                stroke = stroke,
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4f, 12f)
                lineTo(20f, 12f)
                moveTo(8f, 8f)
                lineTo(4f, 12f)
                lineTo(8f, 16f)
                moveTo(16f, 8f)
                lineTo(20f, 12f)
                lineTo(16f, 16f)
            }
        }
    }

    /** Abspielen / Öffnen. */
    val Play: ImageVector by lazy {
        icon("Play") {
            path(
                stroke = stroke,
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(6.5f, 4.8f)
                lineTo(19f, 12f)
                lineTo(6.5f, 19.2f)
                close()
            }
        }
    }

    /** Durchgestrichenes Auge (App ausblenden). */
    val EyeOff: ImageVector by lazy {
        icon("EyeOff") {
            path(
                stroke = stroke,
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(3f, 3f)
                lineTo(21f, 21f)
                moveTo(10.6f, 10.6f)
                arcTo(2.5f, 2.5f, 0f, false, false, 13.4f, 13.4f)
                moveTo(9.4f, 5.4f)
                arcTo(9.8f, 9.8f, 0f, false, true, 12f, 5f)
                curveTo(16.8f, 5f, 20.5f, 8.3f, 21.8f, 12f)
                curveTo(21.2f, 13.7f, 20.1f, 15.2f, 18.7f, 16.4f)
                moveTo(6.3f, 6.7f)
                curveTo(4.4f, 8f, 3f, 9.8f, 2.2f, 12f)
                curveTo(3.5f, 15.7f, 7.2f, 19f, 12f, 19f)
                curveTo(13.5f, 19f, 14.9f, 18.7f, 16.2f, 18.1f)
            }
        }
    }

    /** Info-Kreis. */
    val Info: ImageVector by lazy {
        icon("Info") {
            path(
                stroke = stroke,
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(21f, 12f)
                arcTo(9f, 9f, 0f, true, true, 3f, 12f)
                arcTo(9f, 9f, 0f, true, true, 21f, 12f)
                moveTo(12f, 16f)
                lineTo(12f, 11.5f)
                moveTo(12f, 8.2f)
                lineTo(12.01f, 8.2f)
            }
        }
    }

    /** Papierkorb (Deinstallieren). */
    val Trash: ImageVector by lazy {
        icon("Trash") {
            path(
                stroke = stroke,
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4f, 6.5f)
                lineTo(20f, 6.5f)
                moveTo(9f, 6.5f)
                lineTo(9f, 4.5f)
                arcTo(1f, 1f, 0f, false, true, 10f, 3.5f)
                lineTo(14f, 3.5f)
                arcTo(1f, 1f, 0f, false, true, 15f, 4.5f)
                lineTo(15f, 6.5f)
                moveTo(6.5f, 6.5f)
                lineTo(7.4f, 19.2f)
                arcTo(1.5f, 1.5f, 0f, false, false, 8.9f, 20.5f)
                lineTo(15.1f, 20.5f)
                arcTo(1.5f, 1.5f, 0f, false, false, 16.6f, 19.2f)
                lineTo(17.5f, 6.5f)
            }
        }
    }

    /** Aktualisieren. */
    val Refresh: ImageVector by lazy {
        icon("Refresh") {
            path(
                stroke = stroke,
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(20f, 12f)
                arcTo(8f, 8f, 0f, true, true, 17.6f, 6.3f)
                moveTo(20f, 4f)
                lineTo(20f, 8.5f)
                lineTo(15.5f, 8.5f)
            }
        }
    }

    /** Häkchen. */
    val Check: ImageVector by lazy {
        icon("Check") {
            path(
                stroke = stroke,
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5f, 12.5f)
                lineTo(9.8f, 17.2f)
                lineTo(19.2f, 7.2f)
            }
        }
    }

    /** Pfeil links. */
    val ChevronLeft: ImageVector by lazy {
        icon("ChevronLeft") {
            path(
                stroke = stroke,
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(15f, 6f)
                lineTo(9f, 12f)
                lineTo(15f, 18f)
            }
        }
    }

    /** Pfeil rechts. */
    val ChevronRight: ImageVector by lazy {
        icon("ChevronRight") {
            path(
                stroke = stroke,
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(9f, 6f)
                lineTo(15f, 12f)
                lineTo(9f, 18f)
            }
        }
    }

    /** Film/Medien (Jellyfin-Kategorie). */
    val Film: ImageVector by lazy {
        icon("Film") {
            path(
                stroke = stroke,
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4f, 4f)
                lineTo(20f, 4f)
                arcTo(1.5f, 1.5f, 0f, false, true, 21.5f, 5.5f)
                lineTo(21.5f, 18.5f)
                arcTo(1.5f, 1.5f, 0f, false, true, 20f, 20f)
                lineTo(4f, 20f)
                arcTo(1.5f, 1.5f, 0f, false, true, 2.5f, 18.5f)
                lineTo(2.5f, 5.5f)
                arcTo(1.5f, 1.5f, 0f, false, true, 4f, 4f)
                close()
                moveTo(7f, 4f)
                lineTo(7f, 20f)
                moveTo(17f, 4f)
                lineTo(17f, 20f)
                moveTo(2.5f, 12f)
                lineTo(21.5f, 12f)
            }
        }
    }

    /** Raster (Apps). */
    val Grid: ImageVector by lazy {
        icon("Grid") {
            path(
                stroke = stroke,
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4f, 4f); lineTo(10f, 4f); lineTo(10f, 10f); lineTo(4f, 10f); close()
                moveTo(14f, 4f); lineTo(20f, 4f); lineTo(20f, 10f); lineTo(14f, 10f); close()
                moveTo(4f, 14f); lineTo(10f, 14f); lineTo(10f, 20f); lineTo(4f, 20f); close()
                moveTo(14f, 14f); lineTo(20f, 14f); lineTo(20f, 20f); lineTo(14f, 20f); close()
            }
        }
    }

    /** Bild / Hintergrund. */
    val Image: ImageVector by lazy {
        icon("Image") {
            path(
                stroke = stroke,
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4.5f, 4f)
                lineTo(19.5f, 4f)
                arcTo(1.5f, 1.5f, 0f, false, true, 21f, 5.5f)
                lineTo(21f, 18.5f)
                arcTo(1.5f, 1.5f, 0f, false, true, 19.5f, 20f)
                lineTo(4.5f, 20f)
                arcTo(1.5f, 1.5f, 0f, false, true, 3f, 18.5f)
                lineTo(3f, 5.5f)
                arcTo(1.5f, 1.5f, 0f, false, true, 4.5f, 4f)
                close()
                moveTo(3.5f, 16.5f)
                lineTo(8.5f, 11.5f)
                lineTo(12.5f, 15.5f)
                lineTo(15.5f, 12.5f)
                lineTo(20.5f, 17.5f)
                moveTo(9.5f, 8.8f)
                arcTo(1.2f, 1.2f, 0f, true, true, 7.1f, 8.8f)
                arcTo(1.2f, 1.2f, 0f, true, true, 9.5f, 8.8f)
            }
        }
    }

    /** Startseite / Layout. */
    val Home: ImageVector by lazy {
        icon("Home") {
            path(
                stroke = stroke,
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(3.5f, 10.5f)
                lineTo(12f, 3.8f)
                lineTo(20.5f, 10.5f)
                moveTo(5.5f, 9.2f)
                lineTo(5.5f, 19.5f)
                arcTo(1f, 1f, 0f, false, false, 6.5f, 20.5f)
                lineTo(17.5f, 20.5f)
                arcTo(1f, 1f, 0f, false, false, 18.5f, 19.5f)
                lineTo(18.5f, 9.2f)
            }
        }
    }
}
