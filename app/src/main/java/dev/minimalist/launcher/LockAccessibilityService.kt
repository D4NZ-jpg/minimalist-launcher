package dev.minimalist.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent

/**
 * Does nothing by itself: it only exists so the launcher may ask Android to lock the screen or open the
 * notification shade with a gesture. It never reads the screen (see res/xml/lock_accessibility_service.xml).
 */
class LockAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    companion object {
        @Volatile var instance: LockAccessibilityService? = null
        val isEnabled get() = instance != null
        fun perform(action: Int): Boolean = instance?.performGlobalAction(action) ?: false
        fun lockScreen(): Boolean = Build.VERSION.SDK_INT >= 28 && perform(GLOBAL_ACTION_LOCK_SCREEN)
    }
}
