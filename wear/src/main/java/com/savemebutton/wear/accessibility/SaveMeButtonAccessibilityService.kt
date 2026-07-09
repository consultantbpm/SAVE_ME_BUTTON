package com.savemebutton.wear.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.savemebutton.wear.PanicButtonBus
import com.savemebutton.wear.WearApplication

/**
 * Captures the watch's upper hardware button and, when it is held past the
 * configured panic threshold, signals [PanicButtonBus] so the foreground
 * MainActivity can start the SAME panic sequence its own key handler would.
 *
 * This exists because on some watches (notably Samsung, where Bixby / SysUi
 * grab the stem/power buttons) the plain activity key path is unreliable — the
 * system swallows the button before the activity sees it. An accessibility
 * service with `flagRequestFilterKeyEvents` receives the key first, so it works
 * even there.
 *
 * SAFETY: this is a PARALLEL capture path only. It does not alter the panic,
 * escalation, SMS, or cancel logic. It merely calls into the existing trigger
 * via the bus. The orchestrator already ignores a `trigger()` while a sequence
 * is running (state != Idle), so there is no risk of a double-start.
 *
 * Behaviour:
 *   ACTION_DOWN → schedule a fire at the hold threshold. If the button is still
 *                 held at the threshold, emit "panic hold completed" (this
 *                 matches the activity's postDelayed-at-hold behaviour, and
 *                 reacts even if some OEMs never deliver ACTION_UP after a long
 *                 system-grabbed hold).
 *   ACTION_UP   → cancel the pending fire if the button was released early.
 *
 * The service ONLY consumes keys while the Save Me app is in the foreground;
 * otherwise it passes everything through (returns false) so the launcher /
 * system keep working and the button is never hijacked device-wide.
 *
 * Privacy: `canRetrieveWindowContent="false"` — key events only, never content.
 */
class SaveMeButtonAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val downTimes = mutableMapOf<Int, Long>()
    private val fireRunnables = mutableMapOf<Int, Runnable>()

    @Volatile
    private var foregroundPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "service connected — capturing panic button")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            if (pkg != foregroundPackage) {
                foregroundPackage = pkg
            }
        }
    }

    override fun onInterrupt() {
        fireRunnables.values.forEach { handler.removeCallbacks(it) }
        fireRunnables.clear()
        downTimes.clear()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!isPanicKey(event.keyCode)) return false
        // Only act while our app is on top; otherwise pass through untouched so
        // we never hijack the button system-wide.
        val fg = foregroundPackage
        if (fg == null || fg != packageName) return false

        return when (event.action) {
            KeyEvent.ACTION_DOWN -> handleDown(event)
            KeyEvent.ACTION_UP -> handleUp(event)
            else -> true
        }
    }

    private fun handleDown(event: KeyEvent): Boolean {
        if (event.repeatCount != 0) return true
        downTimes[event.keyCode] = event.eventTime
        val holdMs = resolveHoldMs()
        val keyCode = event.keyCode
        val runnable = Runnable {
            fireRunnables.remove(keyCode)
            downTimes.remove(keyCode)
            Log.d(TAG, "panic hold threshold reached (${holdMs}ms) key=$keyCode → trigger")
            PanicButtonBus.emitPanicHoldCompleted()
        }
        fireRunnables[keyCode] = runnable
        handler.postDelayed(runnable, holdMs)
        return true
    }

    private fun handleUp(event: KeyEvent): Boolean {
        downTimes.remove(event.keyCode)
        val scheduled = fireRunnables.remove(event.keyCode)
        if (scheduled != null) {
            handler.removeCallbacks(scheduled)
            Log.d(TAG, "panic button released before threshold key=${event.keyCode} — canceled")
        }
        return true
    }

    /**
     * Read the configured hold duration (seconds) and convert to ms. Falls back
     * to [DEFAULT_HOLD_MS] if the config can't be read for any reason. The value
     * is the SAME `holdSeconds` the activity's key handler uses.
     */
    private fun resolveHoldMs(): Long {
        val app = application as? WearApplication ?: return DEFAULT_HOLD_MS
        return runCatching {
            // holdSeconds is already normalized to 3..5 by SosConfig.normalized().
            app.configRepository.config.value.holdSeconds * 1000L
        }.getOrDefault(DEFAULT_HOLD_MS)
    }

    private fun isPanicKey(keyCode: Int): Boolean = when (keyCode) {
        // Upper-button / stem keys plus VOLUME_DOWN — the keys the activity's
        // isTriggerKey() accepts (STEM_PRIMARY, VOLUME_DOWN) plus the common
        // alternates OEMs map the upper button to.
        KeyEvent.KEYCODE_STEM_PRIMARY,
        KeyEvent.KEYCODE_STEM_1,
        KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_POWER -> true
        else -> false
    }

    companion object {
        private const val TAG = "SmbButtonA11y"
        private const val DEFAULT_HOLD_MS = 3000L
    }
}
