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

/**
 * Google-Drive-backed entitlement protection **without any user
 * authentication**. Ported from the Crown/Camera family.
 *
 * Talks to a small **Google Apps Script web app** the developer deploys
 * against their own Google Sheet / Drive. A per-install device id keys a
 * tiny record — trial start timestamp + purchased flag — that is
 * reconciled with [PremiumManager] on launch: keep the EARLIEST trial
 * start (anti-reset) and OR-in a purchase. The merged record is written
 * back. Every failure is swallowed; when [ENTITLEMENT_ENDPOINT_URL] is
 * blank the remote step is skipped entirely (local-only).
 */
class DriveEntitlementStore(private val context: Context) {

    companion object {
        private const val TAG = "SmbDriveEnt"

        /**
         * Deployed Google Apps Script web-app URL (e.g.
         * "https://script.google.com/macros/s/XXXX/exec"). Leave blank to
         * run local-only until the developer deploys the endpoint.
         *
         * Expected contract:
         *  - GET  ?action=get&secret=..&deviceId=..  -> {"trialStart":<long>,"purchased":<bool>} or {}
         *  - POST {secret,deviceId,trialStart,purchased,updatedAt}   -> stores/updates the row
         */
        const val ENTITLEMENT_ENDPOINT_URL = ""

        /** Shared secret checked by the Apps Script to reject random callers. */
        const val ENTITLEMENT_SHARED_SECRET = "savemebutton-CHANGE-ME"

        private const val PREFS = "premium_prefs"
        private const val KEY_DEVICE_ID = "device_id"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val deviceId: String by lazy { resolveDeviceId() }

    @SuppressLint("HardwareIds")
    private fun resolveDeviceId(): String {
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()
        return if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
            androidId
        } else {
            prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
                prefs.edit().putString(KEY_DEVICE_ID, it).apply()
            }
        }
    }

    private val enabled: Boolean get() = ENTITLEMENT_ENDPOINT_URL.isNotBlank()

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
