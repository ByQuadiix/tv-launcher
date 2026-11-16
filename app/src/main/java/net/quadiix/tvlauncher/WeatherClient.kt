package net.quadiix.tvlauncher

import org.json.JSONObject
import java.net.URLEncoder
import kotlin.math.roundToInt

/** Wetter über Open-Meteo (kostenlos, ohne API-Schlüssel). */
object WeatherClient {

    data class Place(val name: String, val lat: Double, val lon: Double)

    suspend fun geocode(query: String): Place? {
        val url = "https://geocoding-api.open-meteo.com/v1/search?count=1&language=de&name=" +
            URLEncoder.encode(query.trim(), "UTF-8")
        val results = JSONObject(httpRequest(url)).optJSONArray("results") ?: return null
        if (results.length() == 0) return null
        val o = results.getJSONObject(0)
        return Place(o.getString("name"), o.getDouble("latitude"), o.getDouble("longitude"))
    }

    suspend fun current(lat: Double, lon: Double, city: String): Weather {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code,is_day" +
            "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1"
        val o = JSONObject(httpRequest(url))
        val cur = o.getJSONObject("current")
        val daily = o.getJSONObject("daily")
        return Weather(
            temp = cur.getDouble("temperature_2m").roundToInt(),
            min = daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
            max = daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
            code = cur.getInt("weather_code"),
            isDay = cur.optInt("is_day", 1) == 1,
            city = city,
        )
    }

    /** WMO-Wettercode → (Symbol, Beschreibung). */
    fun describe(code: Int, isDay: Boolean): Pair<String, String> = when (code) {
        0 -> (if (isDay) "☀️" else "🌙") to "Klar"
        1 -> (if (isDay) "🌤️" else "🌙") to "Überwiegend klar"
        2 -> "⛅" to "Teilweise bewölkt"
        3 -> "☁️" to "Bewölkt"
        45, 48 -> "🌫️" to "Nebel"
        51, 53, 55, 56, 57 -> "🌦️" to "Nieselregen"
        61, 63, 65, 66, 67 -> "🌧️" to "Regen"
        71, 73, 75, 77 -> "🌨️" to "Schnee"
        80, 81, 82 -> "🌦️" to "Regenschauer"
        85, 86 -> "🌨️" to "Schneeschauer"
        95, 96, 99 -> "⛈️" to "Gewitter"
        else -> "🌡️" to ""
    }
}
