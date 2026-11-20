package net.quadiix.tvlauncher

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
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

enum class NavEvent { HOME, BACK }

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    private val jf = JellyfinClient(prefs)

    // Apps & Einstellungen
    val apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val favorites = MutableStateFlow(prefs.favorites)
    val hiddenApps = MutableStateFlow(prefs.hiddenApps)
    val hideFavoritesInAllApps = MutableStateFlow(prefs.hideFavoritesInAllApps)
    val showHdmiQuickAction = MutableStateFlow(prefs.showHdmiQuickAction)
    val tvInputs = MutableStateFlow<List<TvInputEntry>>(emptyList())

    // Jellyfin
    val jfLoggedIn = MutableStateFlow(prefs.jfToken != null && prefs.jfUserId != null)
    val jfUser = MutableStateFlow(prefs.jfUserName)
    val jfServer = MutableStateFlow(prefs.jfServer)
    val jfMessage = MutableStateFlow<String?>(null)
    val jfLoading = MutableStateFlow(false)
    val quickConnectCode = MutableStateFlow<String?>(null)

    /** Getrennte Jellyfin-Kategorien für die Top Shelf. */
    val jfResume = MutableStateFlow<List<JfItem>>(emptyList())
    val jfNextUp = MutableStateFlow<List<JfItem>>(emptyList())
    val jfLatest = MutableStateFlow<List<JfItem>>(emptyList())
    val activeCategory = MutableStateFlow(ShelfCategory.RESUME)

    /** Titel, dessen Bild und Infos oben gezeigt werden. */
    val featured = MutableStateFlow<JfItem?>(null)

    // Hintergrund
    val wallpaperMode = MutableStateFlow(prefs.wallpaperMode)
    val wallpaperUrl = MutableStateFlow(prefs.wallpaperUrl)

    // Navigation
    val showSettings = MutableStateFlow(false)
    /** Home- oder Zurück-Taste von der Activity, wird kontextabhängig im UI behandelt. */
    val navEvents = MutableSharedFlow<NavEvent>(extraBufferCapacity = 2)

    private var featuredJob: Job? = null
    private var quickConnectJob: Job? = null
    private var jellyfinJob: Job? = null
    private var lastJellyfin = 0L
    private var categorySelectedManually = false

    fun onResume() {
        // App-Liste nur einmal laden; danach aktualisiert sie der Paket-Empfänger in MainActivity.
        if (apps.value.isEmpty()) refreshApps()
        if (tvInputs.value.isEmpty()) refreshTvInputs()
        if (System.currentTimeMillis() - lastJellyfin > 60_000L) refreshJellyfin()
    }

    // ---------- Apps, Favoriten & Ausblenden ----------

    fun refreshApps() {
        viewModelScope.launch {
            apps.value = withContext(Dispatchers.IO) { AppRepository.load(getApplication()) }
        }
    }

    fun refreshTvInputs() {
        viewModelScope.launch {
            tvInputs.value = withContext(Dispatchers.IO) { TvInputs.load(getApplication()) }
        }
    }

    fun openTvInput(context: Context, input: TvInputEntry) {
        TvInputs.open(context, input)
    }

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

    fun hideApp(pkg: String) {
        val nextHidden = hiddenApps.value + pkg
        prefs.hiddenApps = nextHidden
        hiddenApps.value = nextHidden
        if (pkg in favorites.value) {
            saveFavorites(favorites.value.filterNot { it == pkg })
        }
    }

    fun unhideApp(pkg: String) {
        val nextHidden = hiddenApps.value - pkg
        prefs.hiddenApps = nextHidden
        hiddenApps.value = nextHidden
    }

    fun setHideFavoritesInAllApps(hide: Boolean) {
        prefs.hideFavoritesInAllApps = hide
        hideFavoritesInAllApps.value = hide
    }

    fun setShowHdmiQuickAction(show: Boolean) {
        prefs.showHdmiQuickAction = show
        showHdmiQuickAction.value = show
    }

    private fun saveFavorites(list: List<String>) {
        prefs.favorites = list
        favorites.value = list
    }

    /** [options] enthält die Zoom-Animation aus der Kachel heraus (siehe [scaleUpOptions]). */
    fun launchApp(context: Context, app: AppEntry, options: Bundle? = null) =
        startSafely(context, app.launchIntent, options)

    // ---------- Jellyfin ----------

    fun itemsFor(category: ShelfCategory): List<JfItem> = when (category) {
        ShelfCategory.RESUME -> jfResume.value
        ShelfCategory.NEXT_UP -> jfNextUp.value
        ShelfCategory.LATEST -> jfLatest.value
    }

    fun hasAnyShelfItems(): Boolean =
        jfResume.value.isNotEmpty() || jfNextUp.value.isNotEmpty() || jfLatest.value.isNotEmpty()

    fun refreshJellyfin() {
        if (!jfLoggedIn.value) return
        lastJellyfin = System.currentTimeMillis()
        jellyfinJob?.cancel()
        jellyfinJob = viewModelScope.launch {
            jfLoading.value = true
            try {
                val (rList, nList, lList) = coroutineScope {
                    val r = async { jf.resume().distinctBy { it.id }.take(20) }
                    val n = async { jf.nextUp().distinctBy { it.id }.take(20) }
                    val l = async { jf.latest().distinctBy { it.id }.take(24) }
                    Triple(r.await(), n.await(), l.await())
                }
                jfResume.value = rList
                jfNextUp.value = nList
                jfLatest.value = lList
                if (jfMessage.value?.startsWith("Jellyfin nicht") == true) jfMessage.value = null

                // Falls noch keine Kategorie gewählt oder die aktive leer ist, erste gefüllte wählen
                val currentItems = itemsFor(activeCategory.value)
                if (!categorySelectedManually || currentItems.isEmpty()) {
                    val best = when {
                        rList.isNotEmpty() -> ShelfCategory.RESUME
                        nList.isNotEmpty() -> ShelfCategory.NEXT_UP
                        lList.isNotEmpty() -> ShelfCategory.LATEST
                        else -> activeCategory.value
                    }
                    activeCategory.value = best
                }

                val activeList = itemsFor(activeCategory.value)
                if (featured.value == null || activeList.none { it.id == featured.value?.id }) {
                    featured.value = activeList.firstOrNull()
                        ?: rList.firstOrNull()
                        ?: nList.firstOrNull()
                        ?: lList.firstOrNull()
                }
                // Bilder vorab laden, damit beim Kategorie-Wechsel und Blättern nichts nachlädt
                val all = (rList.take(10) + nList.take(10) + lList.take(10)).distinctBy { it.id }
                Images.preload(getApplication(), all)
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
            } finally {
                jfLoading.value = false
            }
        }
    }

    fun selectCategory(category: ShelfCategory, immediateFeature: Boolean = false) {
        categorySelectedManually = true
        if (activeCategory.value == category && !immediateFeature) return
        activeCategory.value = category
        val first = itemsFor(category).firstOrNull()
        featuredJob?.cancel()
        if (immediateFeature) {
            if (first != null) featured.value = first
        } else if (first != null) {
            featuredJob = viewModelScope.launch {
                delay(180)
                featured.value = first
            }
        }
    }

    /** Jellyfin-Kachel im Dock ausgewählt: oben den ersten Titel der aktiven Kategorie zeigen. */
    fun onJellyfinTileFocused() {
        featuredJob?.cancel()
        val list = itemsFor(activeCategory.value)
        if (featured.value == null || list.none { it.id == featured.value?.id }) {
            list.firstOrNull()?.let { featured.value = it }
        }
    }

    /** Titel in der Shelf ausgewählt: Bild und Infos oben wechseln (kurz verzögert beim Durchblättern). */
    fun onShelfItemFocused(item: JfItem) {
        featuredJob?.cancel()
        featuredJob = viewModelScope.launch {
            delay(250)
            featured.value = item
        }
    }

    /** Markiert einen Titel auf dem Jellyfin-Server als gesehen und aktualisiert die Listen. */
    fun markPlayed(context: Context, item: JfItem) {
        viewModelScope.launch {
            try {
                jf.markPlayed(item.id)
                jfResume.value = jfResume.value.filterNot { it.id == item.id }
                jfNextUp.value = jfNextUp.value.filterNot { it.id == item.id }
                if (featured.value?.id == item.id) {
                    featured.value = itemsFor(activeCategory.value).firstOrNull()
                }
                Toast.makeText(context, "Als gesehen markiert", Toast.LENGTH_SHORT).show()
                refreshJellyfin()
            } catch (e: Exception) {
                Toast.makeText(context, "Fehlgeschlagen: ${e.message ?: "Fehler"}", Toast.LENGTH_SHORT).show()
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
        categorySelectedManually = false
        refreshJellyfin()
    }

    fun logout() {
        prefs.jfToken = null
        prefs.jfUserId = null
        prefs.jfUserName = null
        jfLoggedIn.value = false
        jfUser.value = null
        jfMessage.value = null
        jfResume.value = emptyList()
        jfNextUp.value = emptyList()
        jfLatest.value = emptyList()
        featured.value = null
        categorySelectedManually = false
    }

    /** Öffnet den Titel direkt in der offiziellen Jellyfin-App. */
    fun openInJellyfin(context: Context, item: JfItem, options: Bundle? = null) {
        val intent = Intent()
            .setClassName(JELLYFIN_PKG, "$JELLYFIN_PKG.ui.startup.StartupActivity")
            .putExtra("ItemId", item.id)
            .putExtra("ItemIsUserView", item.isView)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        try {
            context.startActivity(intent, options)
        } catch (_: Exception) {
            val fallback = context.packageManager.getLeanbackLaunchIntentForPackage(JELLYFIN_PKG)
            if (fallback != null) startSafely(context, fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options)
            else Toast.makeText(context, "Die Jellyfin-App ist nicht installiert", Toast.LENGTH_LONG).show()
        }
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
        refreshTvInputs()
        showSettings.value = true
    }

    fun closeSettings() {
        cancelQuickConnect()
        showSettings.value = false
    }

    fun onBack() {
        navEvents.tryEmit(NavEvent.BACK)
    }

    fun onHomePressed() {
        closeSettings()
        navEvents.tryEmit(NavEvent.HOME)
    }

    companion object {
        const val JELLYFIN_PKG = "org.jellyfin.androidtv"
    }
}

fun startSafely(context: Context, intent: Intent, options: Bundle? = null) {
    try {
        context.startActivity(intent, options)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Kann nicht geöffnet werden", Toast.LENGTH_SHORT).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, "Keine Berechtigung zum Öffnen", Toast.LENGTH_SHORT).show()
    }
}
