package net.quadiix.tvlauncher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class HttpException(val code: Int, message: String) : Exception(message)

/** Kleiner HTTP-Helfer ohne Zusatzbibliothek. */
suspend fun httpRequest(
    url: String,
    method: String = "GET",
    headers: Map<String, String> = emptyMap(),
    body: JSONObject? = null,
): String = withContext(Dispatchers.IO) {
    val c = URL(url).openConnection() as HttpURLConnection
    try {
        c.requestMethod = method
        c.connectTimeout = 6000
        c.readTimeout = 12000
        c.setRequestProperty("Accept", "application/json")
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toString().toByteArray()) }
        } else if (method == "POST") {
            c.doOutput = true
            c.setFixedLengthStreamingMode(0)
        }
        val code = c.responseCode
        if (code !in 200..299) throw HttpException(code, "HTTP $code")
        c.inputStream.bufferedReader().use { it.readText() }
    } finally {
        c.disconnect()
    }
}

/** Liefert null für fehlende, leere oder JSON-null-Felder. */
fun JSONObject.str(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
