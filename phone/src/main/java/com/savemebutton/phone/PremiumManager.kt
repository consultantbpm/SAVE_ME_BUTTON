package com.savemebutton.phone

import android.content.Context
import android.content.SharedPreferences

/**
 * Local (on-device) entitlement + trial state for Save Me Button's OPTIONAL,
 * NON-SAFETY premium tier. Ported from the Crown/Camera family.
 *
 * IMPORTANT — SAFETY: nothing on the panic/SOS path (trigger, escalation, SMS,
 * calls to contacts, cancel) ever consults this class. Save Me Button's core
 * safety features are free, forever, for everyone. Premium unlocks only the
 * cosmetic ability to edit the custom SMS message body; free users keep the
 * default message ("I need help. My location:") which is fully functional.
 *
 * Freemium model: while the 24-hour free trial is active OR the one-time
 * unlock has been purchased, [hasFullAccess] is true. The one-time Google Play
 * unlock is product id `savemebutton_premium` (2.99 EUR). Debug builds are
 * always unlocked (see [debugUnlock]).
 *
 * This local state is reconciled with a no-auth Google Drive / Apps-Script
 * endpoint by [com.savemebutton.phone.billing.DriveEntitlementStore] via
 * [reconcileFromRemote] so that clearing app data or reinstalling neither
 * resets the trial nor loses a purchase.
 *
 * Storage: `savemebutton_premium`, keys `is_premium` and
 * `trial_start_timestamp`.
 */
class PremiumManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("savemebutton_premium", Context.MODE_PRIVATE)
    private val isDebugBuild: Boolean =
        (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

    companion object {
        const val KEY_TRIAL_START = "trial_start_timestamp"
        const val KEY_IS_PREMIUM = "is_premium"
        /** Free-trial length: 24 hours from first launch. */
        const val TRIAL_DURATION_MS = 24L * 60L * 60L * 1000L
    }

    init {
        if (!prefs.contains(KEY_TRIAL_START)) {
            prefs.edit().putLong(KEY_TRIAL_START, System.currentTimeMillis()).apply()
        }
    }

    /** Debug builds are always fully unlocked; never forced on in release. */
    private val debugUnlock: Boolean get() = isDebugBuild

    /** True once the one-time purchase has been recorded locally. */
    val isPurchased: Boolean get() = prefs.getBoolean(KEY_IS_PREMIUM, false)

    /** Effective premium entitlement = debug build OR purchased. */
    val isPremium: Boolean get() = debugUnlock || isPurchased

    val trialStartTime: Long
        get() = prefs.getLong(KEY_TRIAL_START, System.currentTimeMillis())

    val remainingTrialTimeMs: Long
        get() {
            if (isPremium) return TRIAL_DURATION_MS
            val elapsed = System.currentTimeMillis() - trialStartTime
            return (TRIAL_DURATION_MS - elapsed).coerceAtLeast(0L)
        }

    val isTrialExpired: Boolean
        get() {
            if (isPremium) return false
            val elapsed = System.currentTimeMillis() - trialStartTime
            return elapsed > TRIAL_DURATION_MS || elapsed < 0
        }

    /**
     * Cosmetic-feature gate: full access while trial runs OR the app is
     * premium. NEVER gate anything safety-critical on this.
     */
    val hasFullAccess: Boolean get() = isPremium || !isTrialExpired

    fun setPurchased(value: Boolean) {
        prefs.edit().putBoolean(KEY_IS_PREMIUM, value).apply()
    }

    /**
     * Reconcile with a record restored from the remote endpoint: keep the
     * EARLIEST trial start (so a reinstall / clear-data cannot reset the clock)
     * and OR-in a restored purchase. Returns true if anything changed locally.
     */
    fun reconcileFromRemote(remoteTrialStart: Long?, remotePurchased: Boolean): Boolean {
        var changed = false
        val editor = prefs.edit()
        if (remoteTrialStart != null && remoteTrialStart > 0L && remoteTrialStart < trialStartTime) {
            editor.putLong(KEY_TRIAL_START, remoteTrialStart)
            changed = true
        }
        if (remotePurchased && !isPurchased) {
            editor.putBoolean(KEY_IS_PREMIUM, true)
            changed = true
        }
        if (changed) editor.apply()
        return changed
    }
}
