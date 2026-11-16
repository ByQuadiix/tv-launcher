package net.quadiix.tvlauncher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Notlösung für Google TV: Ist dieser Bedienungshilfe-Dienst aktiv, öffnet die Home-Taste
 * den TV Launcher, auch wenn die Google-TV-Startseite noch eingeschaltet ist.
 */
class HomeKeyService : AccessibilityService() {

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        Log.d("TVLauncherHome", "Home-Taste abgefangen, action=${event.action}")
        if (event.action == KeyEvent.ACTION_UP) {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            )
        }
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit
}
