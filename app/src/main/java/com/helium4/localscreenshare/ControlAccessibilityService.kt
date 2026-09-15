package com.helium4.localscreenshare

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import com.helium4.localscreenshare.AppLogger
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

private const val LOGTAG = "ZeroLink"

/**
 * Replays remote control input on the device being shared (the sender).
 *
 * This is the only non-root way an ordinary app can inject taps/swipes into other
 * apps: gestures are dispatched via [dispatchGesture] and system navigation via
 * [performGlobalAction]. The user must enable this service once in
 * Settings ▸ Accessibility. [ScreenShareService] reaches the running instance
 * through the [instance] singleton (same process).
 */
class ControlAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        AppLogger.i(LOGTAG, "[A11y] Control service connected")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        AppLogger.i(LOGTAG, "[A11y] Control service unbound")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    /** Single tap (x, y in sender pixels). */
    fun tap(x: Float, y: Float) = dispatchTap(x, y, TAP_DURATION_MS)

    /** Long press at (x, y). */
    fun longPress(x: Float, y: Float) = dispatchTap(x, y, LONG_PRESS_DURATION_MS)

    private fun dispatchTap(x: Float, y: Float, durationMs: Long) {
        val path = Path().apply {
            moveTo(x, y)
            // A tiny non-zero segment keeps the stroke valid on strict OEM gesture engines.
            lineTo(x + 1f, y)
        }
        safeDispatch(path, durationMs)
    }

    /** Swipe / drag / scroll from start to end over [durationMs]. */
    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Int) {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        safeDispatch(path, durationMs.coerceIn(20, 5000).toLong())
    }

    private fun safeDispatch(path: Path, durationMs: Long) {
        try {
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(1))
            val ok = dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
            if (!ok) AppLogger.w(LOGTAG, "[A11y] dispatchGesture returned false")
        } catch (e: Exception) {
            AppLogger.e(LOGTAG, "[A11y] dispatchGesture failed: ${e.message}")
        }
    }

    /** System navigation buttons. */
    fun globalAction(action: Byte) {
        val id = when (action) {
            ControlProtocol.GLOBAL_BACK -> GLOBAL_ACTION_BACK
            ControlProtocol.GLOBAL_HOME -> GLOBAL_ACTION_HOME
            ControlProtocol.GLOBAL_RECENTS -> GLOBAL_ACTION_RECENTS
            else -> return
        }
        performGlobalAction(id)
    }

    /**
     * Best-effort text injection into the currently focused input field.
     * Works on standard editable nodes; appends to existing content.
     */
    fun typeText(text: String) {
        val node: AccessibilityNodeInfo =
            rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: run {
                AppLogger.w(LOGTAG, "[A11y] No focused input to type into")
                return
            }
        val existing = node.text?.toString() ?: ""
        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                existing + text
            )
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    companion object {
        @Volatile
        var instance: ControlAccessibilityService? = null
            private set

        private const val TAP_DURATION_MS = 60L
        private const val LONG_PRESS_DURATION_MS = 700L
    }
}
