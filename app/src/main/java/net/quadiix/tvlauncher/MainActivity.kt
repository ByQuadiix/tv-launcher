package net.quadiix.tvlauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import net.quadiix.tvlauncher.ui.HomeScreen

class MainActivity : ComponentActivity() {

    private val vm: LauncherViewModel by viewModels()

    /** Lädt die App-Liste neu, wenn Apps installiert, entfernt oder aktualisiert werden. */
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = vm.refreshApps()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ein Launcher darf nie mit "Zurück" geschlossen werden.
        onBackPressedDispatcher.addCallback(this) { vm.onBack() }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED)

        setContent { HomeScreen(vm) }
        // Compose malt immer den ganzen Bildschirm (Filmbild oder Farbverlauf). Den schwarzen
        // Fensterhintergrund nach dem ersten Bild weglassen spart eine komplette Vollbild-Schicht
        // pro Bild – auf der schwachen TV-GPU spürbar.
        window.decorView.post { window.setBackgroundDrawable(null) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home erneut gedrückt, während der Launcher schon offen ist
        if (intent.hasCategory(Intent.CATEGORY_HOME)) vm.onHomePressed()
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        super.onDestroy()
    }
}
