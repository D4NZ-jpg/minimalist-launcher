package dev.minimalist.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager

/** Things gestures can trigger in the rest of the system. Each returns false when it could not do it. */
class SystemActions(private val context: Context) {
    private val camera = context.getSystemService(CameraManager::class.java)
    private var torchOn = false
    private val torchId: String? = runCatching {
        camera.cameraIdList.firstOrNull { id ->
            val c = camera.getCameraCharacteristics(id)
            c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true && c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
    }.getOrNull()

    init {
        runCatching {
            camera.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) { if (cameraId == torchId) torchOn = enabled }
            }, null)
        }
    }

    fun toggleFlashlight(): Boolean = torchId?.let { runCatching { camera.setTorchMode(it, !torchOn); true }.getOrDefault(false) } ?: false

    fun expandNotifications(): Boolean = expand("expandNotificationsPanel") || LockAccessibilityService.perform(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
    fun expandQuickSettings(): Boolean = expand("expandSettingsPanel") || LockAccessibilityService.perform(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
    fun lockScreen(): Boolean = LockAccessibilityService.lockScreen()

    /** The status bar service has no public API for this; it is reachable with the EXPAND_STATUS_BAR permission. */
    private fun expand(method: String): Boolean = runCatching {
        val service = context.getSystemService("statusbar") ?: return false
        Class.forName("android.app.StatusBarManager").getMethod(method).invoke(service)
        true
    }.getOrDefault(false)
}
