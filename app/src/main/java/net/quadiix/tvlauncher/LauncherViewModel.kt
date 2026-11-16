package net.quadiix.tvlauncher

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    private val jf = JellyfinClient(prefs)

    // Apps
    val apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val favorites = MutableStateFlow(prefs.favorites)

    // Jellyfin
    val jfLoggedIn = MutableStateFlow(prefs.jfToken != null && prefs.jfUserId != null)
    val jfUser = MutableStateFlow(prefs.jfUserName)
    val jfServer = MutableStateFlow(prefs.jfServer)
    val jfMessage = MutableStateFlow<String?>(null)
    val quickConnectCode = MutableStateFlow<String?>(null)
    val resume = MutableStateFlow<List<JfItem>>(emptyList())
    val nextUp = MutableStateFlow<List<JfItem>>(emptyList())
    val latest = MutableStateFlow<List<JfItem>>(emptyList())
    val views = MutableStateFlow<List<JfItem>>(emptyList())

    // Wetter
    val weather = MutableStateFlow<Weather?>(null)
    val weatherEnabled = MutableStateFlow(prefs.weatherEnabled)
    val weatherCity = MutableStateFlow(prefs.weatherCity)

    // Hintergrund
    val wallpaperMode = MutableStateFlow(prefs.wallpaperMode)
    val wallpaperUrl = MutableStateFlow(prefs.wallpaperUrl)
    val backdrop = MutableStateFlow<String?>(null)
    /** Titel, der oben groß gezeigt wird (zuletzt ausgewählter Jellyfin-Titel). */
    val featured = MutableStateFlow<JfItem?>(null)

    // Navigation
    val showSettings = MutableStateFlow(false)
    /** Wird ausgelöst, wenn Home/Zurück gedrückt wird: Startseite nach oben scrollen. */
    val homeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private var lastWeather = 0L
    private var backdropJob: Job? = null
    private var quickConnectJob: Job? = null
    private var jellyfinJob: Job? = null

    private var lastJellyfin = 0L

    fun onResume() {
        // App-Liste nur einmal laden; danach aktualisiert sie der Paket-Empfänger in MainActivity.
        // (Früher bei jedem Zurückkehren: alle Banner neu dekodiert → Ruckler genau beim Home-Drücken.)
        if (apps.value.isEmpty()) refreshApps()
        val now = System.currentTimeMillis()
        if (now - lastJellyfin > 60_000L) refreshJellyfin()
        if (now - lastWeather > 30 * 60_000L) refreshWeather()
    }

    // ---------- Apps & Favoriten ----------

    fun refreshApps() {
        viewModelScope.launch {
            apps.value = withContext(Dispatchers.IO) { AppRepository.load(getApplication()) }
        }
    }

    fun isFavorite(pkg: String) = pkg in favorites.value

    fun toggleFavorite(pkg: String) {
        val list = favorites.value.toMutableList()
        if (!list.remove(pkg)) list.add(pkg)
        saveFavorites(list)
    }

    fun moveFavorite(pkg: String, delta: Int) {
        val list = favorites.value.toMutableList()
        val i = list.indexOf(pkg)
        val j = i + delta
        if (i < 0 || j !in list.indices) return
        list[i] = list[j].also { list[j] = list[i] }
        saveFavorites(list)
    }

    private fun saveFavorites(list: List<String>) {
        prefs.favorites = list
        favorites.value = list
    }

    fun launchApp(context: Context, app: AppEntry) = startSafely(context, app.launchIntent)

    // ---------- Jellyfin ----------

    fun refreshJellyfin() {
        if (!jfLoggedIn.value) return
        lastJellyfin = System.currentTimeMillis()
        jellyfinJob?.cancel()
        jellyfinJob = viewModelScope.launch {
            try {
                coroutineScope {
                    val r = async { jf.resume() }
                    val n = async { jf.nextUp() }
                    val l = async { jf.latest() }
                    val v = async { jf.views() }
                    val res = r.await()
                    val resIds = res.map { it.id }.toSet()
                    resume.value = res
                    nextUp.value = n.await().filter { it.id !in resIds }
                    latest.value = l.await()
                    views.value = v.await()
                }
                if (jfMessage.value?.startsWith("Jellyfin nicht") == true) jfMessage.value = null
                if (featured.value == null) {
                    val first = (resume.value + nextUp.value + latest.value).firstOrNull { it.backdropUrl != null }
                    featured.value = first
                    if (backdrop.value == null) backdrop.value = first?.backdropUrl
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                if (e.code == 401) {
                    logout()
                    jfMessage.value = "Jellyfin-Anmeldung abgelaufen – bitte in den Einstellungen neu anmelden"
                } else {
                    jfMessage.value = "Jellyfin nicht erreichbar (${e.message})"
                }
            } catch (e: Exception) {
                jfMessage.value = "Jellyfin nicht erreichbar (${e.message ?: e.javaClass.simpleName})"
            }
        }
    }

    fun setServer(input: String) {
        var url = input.trim().trimEnd('/')
        if (url.isEmpty()) return
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://$url"
        prefs.jfServer = url
        jfServer.value = url
    }

    fun loginWithPassword(user: String, password: String) {
        viewModelScope.launch {
            jfMessage.value = "Anmelden …"
            try {
                saveAuth(jf.authenticate(user.trim(), password))
            } catch (e: HttpException) {
                jfMessage.value = if (e.code == 401) "Benutzername oder Passwort falsch" else "Anmeldung fehlgeschlagen (${e.message})"
            } catch (e: Exception) {
                jfMessage.value = "Server nicht erreichbar (${e.message ?: e.javaClass.simpleName})"
            }
        }
    }

    fun startQuickConnect() {
        quickConnectJob?.cancel()
        quickConnectJob = viewModelScope.launch {
            try {
                if (!jf.quickConnectEnabled()) {
                    jfMessage.value = "Quick Connect ist auf dem Server ausgeschaltet (Dashboard → Allgemein → Quick Connect)"
                    return@launch
                }
                val (secret, code) = jf.quickConnectInitiate()
                quickConnectCode.value = code
                jfMessage.value = null
                repeat(100) { // ca. 5 Minuten
                    delay(3000)
                    if (jf.quickConnectAuthorized(secret)) {
                        quickConnectCode.value = null
                        saveAuth(jf.authenticateQuickConnect(secret))
                        return@launch
                    }
                }
                quickConnectCode.value = null
                jfMessage.value = "Quick-Connect-Code abgelaufen, bitte neu starten"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                quickConnectCode.value = null
                jfMessage.value = "Quick Connect fehlgeschlagen (${e.message ?: e.javaClass.simpleName})"
            }
        }
    }

    fun cancelQuickConnect() {
        quickConnectJob?.cancel()
        quickConnectCode.value = null
    }

    private fun saveAuth(a: JellyfinClient.Auth) {
        prefs.jfToken = a.token
        prefs.jfUserId = a.userId
        prefs.jfUserName = a.userName
        jfUser.value = a.userName
        jfLoggedIn.value = true
        jfMessage.value = "Angemeldet als ${a.userName}"
        refreshJellyfin()
    }

    fun logout() {
        prefs.jfToken = null
        prefs.jfUserId = null
        prefs.jfUserName = null
        jfLoggedIn.value = false
        jfUser.value = null
        jfMessage.value = null
        resume.value = emptyList(); nextUp.value = emptyList()
        latest.value = emptyList(); views.value = emptyList()
        backdrop.value = null
        featured.value = null
    }

    /** Öffnet den Titel direkt in der offiziellen Jellyfin-App. */
    fun openInJellyfin(context: Context, item: JfItem) {
        val intent = Intent()
            .setClassName(JELLYFIN_PKG, "$JELLYFIN_PKG.ui.startup.StartupActivity")
            .putExtra("ItemId", item.id)
            .putExtra("ItemIsUserView", item.isView)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            val fallback = context.packageManager.getLeanbackLaunchIntentForPackage(JELLYFIN_PKG)
            if (fallback != null) startSafely(context, fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            else Toast.makeText(context, "Die Jellyfin-App ist nicht installiert", Toast.LENGTH_LONG).show()
        }
    }

    fun onItemFocused(item: JfItem) {
        if (item.isView) return
        backdropJob?.cancel()
        backdropJob = viewModelScope.launch {
            delay(600) // erst wechseln, wenn man auf einem Titel stehen bleibt – nicht beim Durchblättern
            featured.value = item
            item.backdropUrl?.let { backdrop.value = it }
        }
    }

    // ---------- Wetter ----------

    fun refreshWeather() {
        val lat = prefs.weatherLat
        val lon = prefs.weatherLon
        if (!prefs.weatherEnabled || lat == null || lon == null) {
            weather.value = null
            return
        }
        viewModelScope.launch {
            try {
                weather.value = WeatherClient.current(lat, lon, prefs.weatherCity ?: "")
                lastWeather = System.currentTimeMillis()
            } catch (_: Exception) {
                // Beim nächsten Öffnen erneut versuchen
            }
        }
    }

    /** Liefert eine Fehlermeldung oder null bei Erfolg. */
    suspend fun setWeatherCity(name: String): String? {
        if (name.isBlank()) return "Bitte einen Ort eingeben"
        val place = try {
            WeatherClient.geocode(name)
        } catch (e: Exception) {
            return "Wetterdienst nicht erreichbar"
        } ?: return "Ort „$name“ nicht gefunden"
        prefs.weatherCity = place.name
        prefs.weatherLat = place.lat
        prefs.weatherLon = place.lon
        weatherCity.value = place.name
        refreshWeather()
        return null
    }

    fun setWeatherEnabled(enabled: Boolean) {
        prefs.weatherEnabled = enabled
        weatherEnabled.value = enabled
        refreshWeather()
    }

    // ---------- Hintergrund ----------

    fun setWallpaperMode(mode: String) {
        prefs.wallpaperMode = mode
        wallpaperMode.value = mode
    }

    fun setWallpaperCustom(url: String) {
        prefs.wallpaperUrl = url
        wallpaperUrl.value = url
        setWallpaperMode("custom")
    }

    // ---------- Navigation ----------

    fun openSettings() {
        showSettings.value = true
    }

    fun closeSettings() {
        cancelQuickConnect()
        showSettings.value = false
    }

    fun onBack() {
        if (showSettings.value) closeSettings() else homeEvents.tryEmit(Unit)
    }

    fun onHomePressed() {
        closeSettings()
        homeEvents.tryEmit(Unit)
    }

    companion object {
        const val JELLYFIN_PKG = "org.jellyfin.androidtv"
    }
}

fun startSafely(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Kann nicht geöffnet werden", Toast.LENGTH_SHORT).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, "Keine Berechtigung zum Öffnen", Toast.LENGTH_SHORT).show()
    }
}
