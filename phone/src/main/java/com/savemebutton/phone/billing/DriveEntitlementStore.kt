package com.savemebutton.phone.billing

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.util.Log
import com.savemebutton.phone.PremiumManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

/*
 * Canonical implementation shared by the Crown family — keep byte-identical
 * across apps except package/secret/url; sibling copies: crown-button-app-android,
 * nexttrack-button-app-android, camera-button-app-android, save-me-button-app-android,
 * stopwatch-button-app-android, NOTIFICATION READ, BikeQuest.
 */

/**
 * Google-Drive-backed entitlement protection **without any user authentication**.
 *
 * Instead of signing the user into their own Drive (which forces an OAuth consent
 * screen), the app talks to a small **Google Apps Script web app** the developer
 * deploys against their own Google Sheet / Drive. That endpoint is a public HTTPS
 * URL; the app calls it over plain HTTP with a shared secret and a stable
 * per-install device id — no Google sign-in on the device.
 *
 * A tiny record — the trial start timestamp and the purchase flag, keyed by
 * device id — is read on launch and reconciled with the local [PremiumManager]:
 * keep the EARLIEST trial start (so clearing app data / reinstalling cannot reset
 * the one-day trial) and OR-in a purchase. The merged record is written back.
 *
 * NOTE: Play Billing already restores *real purchases* per Google account without
 * this; this endpoint exists as an anti-trial-reset backstop and a remote
 * kill-switch. Everything is best-effort — every failure is swallowed and the app
 * runs on the local record. If the endpoint is unconfigured (blank, or still the
 * "XXXX" placeholder) the remote step is skipped entirely (local-only).
 */
class DriveEntitlementStore(private val context: Context) {

    companion object {
        private const val TAG = "DriveEntitlement"

        /**
         * Deployed Google Apps Script web-app URL, e.g.
         * "https://script.google.com/macros/s/XXXX/exec". Leave blank (or the
         * "XXXX" placeholder) to run local-only until the developer deploys the
         * endpoint.
         *
         * Expected contract:
         *  - GET  ?action=get&secret=..&deviceId=..  -> {"trialStart":<long>,"purchased":<bool>} or {}
         *  - POST {secret,deviceId,trialStart,purchased,updatedAt}  -> stores/updates the row
         */
        const val ENTITLEMENT_ENDPOINT_URL = "https://script.google.com/macros/s/AKfycbyxVku5wXKKDiq6JeWsxk8osE-UvD2sS14KY9q5eiuyUAAYoGMQdC5ewmzLK4KNjBCMNw/exec"

        /** Shared secret checked by the Apps Script to reject random callers. */
        const val ENTITLEMENT_SHARED_SECRET = "savemebutton-ent-UR9ofyRdP6XGzMnYZVsZ"

        private const val PREFS = "premium_prefs"
        private const val KEY_DEVICE_ID = "device_id"

        /** The endpoint counts as configured only if it is a real, non-placeholder https URL. */
        fun isEndpointConfigured(url: String): Boolean =
            url.isNotBlank() && url.startsWith("https://") && !url.contains("XXXX")
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Stable per-install id. Prefers a value already persisted under
     * [KEY_DEVICE_ID] — so a resolution, once made, never flips again even if
     * a later ANDROID_ID read transiently fails — otherwise prefers
     * [Settings.Secure.ANDROID_ID] (survives clear-data / reinstall for the
     * same signing key, which is what makes the anti-trial-reset work),
     * falling back to a persisted random UUID. Whichever value is chosen is
     * persisted immediately so every later call is stable.
     */
    private val deviceId: String by lazy { resolveDeviceId() }

    @SuppressLint("HardwareIds")
    private fun resolveDeviceId(): String {
        prefs.getString(KEY_DEVICE_ID, null)?.let { if (it.isNotBlank()) return it }
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()
        val id = if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
            androidId
        } else {
            UUID.randomUUID().toString()
        }
        prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        return id
    }

    private val enabled: Boolean get() = isEndpointConfigured(ENTITLEMENT_ENDPOINT_URL)

    /**
     * Reconcile local entitlement with the remote record (both directions).
     * No-op when the endpoint is unconfigured or unreachable. Bumps
     * [EntitlementBus] if the local record changed. Safe on every launch.
     */
    suspend fun sync(premiumManager: PremiumManager): Boolean = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext false
        runCatching {
            val remote = fetchRecord()
            val changed = premiumManager.reconcileFromRemote(
                remoteTrialStart = remote?.optLong("trialStart", 0L)?.takeIf { it > 0L },
                remotePurchased = remote?.optBoolean("purchased", false) ?: false,
            )
            val remoteTrial = remote?.optLong("trialStart", 0L) ?: 0L
            val remotePurchased = remote?.optBoolean("purchased", false) ?: false
            val needsWrite = remote == null ||
                remoteTrial != premiumManager.trialStartTime ||
                remotePurchased != premiumManager.isPurchased
            if (needsWrite) {
                postRecord(premiumManager.trialStartTime, premiumManager.isPurchased)
            }
            if (changed) EntitlementBus.bump()
            changed
        }.getOrElse {
            Log.d(TAG, "Entitlement sync skipped: ${it.message}")
            false
        }
    }

    private fun fetchRecord(): JSONObject? {
        val secret = URLEncoder.encode(ENTITLEMENT_SHARED_SECRET, StandardCharsets.UTF_8.name())
        val id = URLEncoder.encode(deviceId, StandardCharsets.UTF_8.name())
        val url = URL("$ENTITLEMENT_ENDPOINT_URL?action=get&secret=$secret&deviceId=$id")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 12000
            instanceFollowRedirects = true
        }
        if (conn.responseCode !in 200..299) return null
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        // Empty record → treat as "no remote entry yet".
        return if (json.has("trialStart") || json.has("purchased")) json else null
    }

    private fun postRecord(trialStart: Long, purchased: Boolean) {
        val payload = JSONObject()
            .put("secret", ENTITLEMENT_SHARED_SECRET)
            .put("deviceId", deviceId)
            .put("trialStart", trialStart)
            .put("purchased", purchased)
            .put("updatedAt", System.currentTimeMillis())
        val conn = (URL(ENTITLEMENT_ENDPOINT_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 12000
            readTimeout = 12000
            instanceFollowRedirects = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }
        OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(payload.toString()) }
        Log.d(TAG, "postRecord response=${conn.responseCode}")
        conn.inputStream.close()
    }
}
