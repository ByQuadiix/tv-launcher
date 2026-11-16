package net.quadiix.tvlauncher

import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** Schlanker Client für die Jellyfin-REST-API (Server ab 10.9). */
class JellyfinClient(private val prefs: Prefs) {

    data class Auth(val token: String, val userId: String, val userName: String)

    private val server get() = prefs.jfServer.trimEnd('/')

    private fun headers(withToken: Boolean = true): Map<String, String> {
        val device = (Build.MODEL ?: "Xiaomi TV").replace("\"", "")
        var h = "MediaBrowser Client=\"TV Launcher\", Device=\"$device\", " +
            "DeviceId=\"${prefs.deviceId}\", Version=\"1.0.0\""
        val token = prefs.jfToken
        if (withToken && token != null) h += ", Token=\"$token\""
        return mapOf("Authorization" to h)
    }

    private suspend fun get(path: String) = httpRequest("$server$path", headers = headers())

    // ---------- Anmeldung ----------

    suspend fun authenticate(user: String, password: String): Auth {
        val body = JSONObject().put("Username", user).put("Pw", password)
        val res = httpRequest("$server/Users/AuthenticateByName", "POST", headers(false), body)
        return parseAuth(res)
    }

    suspend fun quickConnectEnabled(): Boolean =
        httpRequest("$server/QuickConnect/Enabled", headers = headers(false)).trim() == "true"

    /** Startet Quick Connect; liefert (Secret, Code). Den Code bestätigt man in einer anderen Jellyfin-App. */
    suspend fun quickConnectInitiate(): Pair<String, String> {
        val o = JSONObject(httpRequest("$server/QuickConnect/Initiate", "POST", headers(false)))
        return o.getString("Secret") to o.getString("Code")
    }

    suspend fun quickConnectAuthorized(secret: String): Boolean {
        val o = JSONObject(
            httpRequest(
                "$server/QuickConnect/Connect?secret=" + URLEncoder.encode(secret, "UTF-8"),
                headers = headers(false),
            )
        )
        return o.optBoolean("Authenticated", false)
    }

    suspend fun authenticateQuickConnect(secret: String): Auth {
        val body = JSONObject().put("Secret", secret)
        return parseAuth(httpRequest("$server/Users/AuthenticateWithQuickConnect", "POST", headers(false), body))
    }

    private fun parseAuth(res: String): Auth {
        val o = JSONObject(res)
        val user = o.getJSONObject("User")
        return Auth(o.getString("AccessToken"), user.getString("Id"), user.optString("Name"))
    }

    // ---------- Inhalte ----------

    private val uid get() = prefs.jfUserId ?: throw HttpException(401, "Nicht angemeldet")

    suspend fun resume(): List<JfItem> =
        items(JSONObject(get("/UserItems/Resume?userId=$uid&limit=20&mediaTypes=Video&enableTotalRecordCount=false&fields=Overview")).optJSONArray("Items"))

    suspend fun nextUp(): List<JfItem> =
        items(JSONObject(get("/Shows/NextUp?userId=$uid&limit=20&enableTotalRecordCount=false&fields=Overview")).optJSONArray("Items"))

    suspend fun latest(): List<JfItem> =
        items(JSONArray(get("/Items/Latest?userId=$uid&limit=24&fields=Overview")))

    suspend fun views(): List<JfItem> =
        items(JSONObject(get("/UserViews?userId=$uid")).optJSONArray("Items"), isView = true)

    private fun items(arr: JSONArray?, isView: Boolean = false): List<JfItem> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { parseItem(arr.getJSONObject(it), isView) }
    }

    private fun img(itemId: String, kind: String, tag: String?, width: Int) =
        "$server/Items/$itemId/Images/$kind?maxWidth=$width&quality=90" + (tag?.let { "&tag=$it" } ?: "")

    private fun parseItem(o: JSONObject, isView: Boolean): JfItem {
        val id = o.getString("Id")
        val type = o.str("Type") ?: ""
        val tags = o.optJSONObject("ImageTags")
        val primaryTag = tags?.str("Primary")
        val thumbTag = tags?.str("Thumb")
        val backdrops = o.optJSONArray("BackdropImageTags")
        val parentBackdropId = o.str("ParentBackdropItemId")
        val parentBackdrops = o.optJSONArray("ParentBackdropImageTags")
        val parentThumbId = o.str("ParentThumbItemId")
        val parentThumbTag = o.str("ParentThumbImageTag")

        val backdrop = when {
            // 1280 px reichen für den abgedunkelten Hintergrund und sparen gut die Hälfte an Speicher/Dekodierzeit
            backdrops != null && backdrops.length() > 0 -> img(id, "Backdrop", backdrops.getString(0), 1280)
            parentBackdropId != null && parentBackdrops != null && parentBackdrops.length() > 0 ->
                img(parentBackdropId, "Backdrop", parentBackdrops.getString(0), 1280)
            else -> null
        }

        // Karten sind 272 dp = 544 px breit (bei Vergrößerung ~590 px)
        val episodeStill = type == "Episode" && primaryTag != null
        val usesThumb = !isView && !episodeStill && (thumbTag != null || (parentThumbId != null && parentThumbTag != null))
        val landscape = when {
            isView && primaryTag != null -> img(id, "Primary", primaryTag, 600)
            episodeStill -> img(id, "Primary", primaryTag, 600)
            thumbTag != null -> img(id, "Thumb", thumbTag, 600)
            parentThumbId != null && parentThumbTag != null -> img(parentThumbId, "Thumb", parentThumbTag, 600)
            backdrop != null -> backdrop.replace("maxWidth=1280", "maxWidth=600")
            primaryTag != null -> img(id, "Primary", primaryTag, 600)
            else -> null
        }

        val subtitle = when (type) {
            "Episode" -> {
                val s = if (o.has("ParentIndexNumber")) o.optInt("ParentIndexNumber") else null
                val e = if (o.has("IndexNumber")) o.optInt("IndexNumber") else null
                val code = if (s != null && e != null) "S${s} E${e}" else null
                listOfNotNull(o.str("SeriesName"), code).joinToString(" · ")
            }
            "Movie" -> o.optInt("ProductionYear").takeIf { it > 0 }?.toString() ?: "Film"
            "Series" -> {
                val unplayed = o.optJSONObject("UserData")?.optInt("UnplayedItemCount") ?: 0
                if (unplayed > 0) "$unplayed neue Folge${if (unplayed == 1) "" else "n"}" else "Serie"
            }
            "MusicAlbum" -> o.str("AlbumArtist") ?: "Album"
            else -> ""
        }

        val userData = o.optJSONObject("UserData")
        val pct = userData?.optDouble("PlayedPercentage", Double.NaN)
        val progress = pct?.takeIf { !it.isNaN() && it > 0 }?.let { (it / 100.0).toFloat() }

        // Titel-Logo: eigenes oder das der Serie
        val logoTag = tags?.str("Logo")
        val parentLogoId = o.str("ParentLogoItemId")
        val parentLogoTag = o.str("ParentLogoImageTag")
        val logo = when {
            logoTag != null -> img(id, "Logo", logoTag, 600)
            parentLogoId != null && parentLogoTag != null -> img(parentLogoId, "Logo", parentLogoTag, 600)
            else -> null
        }

        // Poster: bei Folgen das Poster der Serie
        val seriesId = o.str("SeriesId")
        val seriesPrimaryTag = o.str("SeriesPrimaryImageTag")
        val poster = when {
            type == "Episode" && seriesId != null && seriesPrimaryTag != null -> img(seriesId, "Primary", seriesPrimaryTag, 300)
            primaryTag != null -> img(id, "Primary", primaryTag, 300)
            else -> null
        }

        val runTicks = o.optLong("RunTimeTicks", 0L)
        val posTicks = userData?.optLong("PlaybackPositionTicks", 0L) ?: 0L
        val remaining = if (runTicks > 0 && posTicks > 0) "Noch " + formatDuration(runTicks - posTicks) else null

        val meta = buildList {
            o.optInt("ProductionYear").takeIf { it > 0 }?.let { add(it.toString()) }
            o.str("OfficialRating")?.let { add(it) }
            if (runTicks > 0 && type != "Series") add(formatDuration(runTicks))
            o.optDouble("CommunityRating", Double.NaN).takeIf { !it.isNaN() && it > 0 }
                ?.let { add("★ " + String.format(java.util.Locale.GERMAN, "%.1f", it)) }
        }.joinToString(" · ")

        return JfItem(
            id = id,
            title = o.str("Name") ?: "",
            subtitle = subtitle,
            imageUrl = landscape,
            backdropUrl = backdrop,
            progress = progress,
            isView = isView,
            logoUrl = logo,
            posterUrl = poster,
            overview = o.str("Overview"),
            meta = meta,
            remaining = remaining,
            imageHasTitle = usesThumb,
        )
    }

    private fun formatDuration(ticks: Long): String {
        val minutes = (ticks / 600_000_000L).toInt().coerceAtLeast(1)
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "$m Min"
            m == 0 -> "$h Std"
            else -> "$h Std $m Min"
        }
    }
}
