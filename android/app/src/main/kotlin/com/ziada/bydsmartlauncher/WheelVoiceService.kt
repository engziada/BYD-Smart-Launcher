package com.ziada.bydsmartlauncher

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/** Optional hardware shortcut. The launcher receives the key only while focused. */
class WheelVoiceService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onKeyEvent(event: KeyEvent): Boolean =
        if (isVoiceKey(event.keyCode)) foregroundHandler?.invoke(event) == true else false

    companion object {
        // Assistant NG's short-press key on its BYD development vehicle.
        private const val BYD_MIC_SHORT_PRESS = 320
        internal var foregroundHandler: ((KeyEvent) -> Boolean)? = null

        internal fun isVoiceKey(code: Int): Boolean =
            code == BYD_MIC_SHORT_PRESS || code == KeyEvent.KEYCODE_VOICE_ASSIST
    }
}
