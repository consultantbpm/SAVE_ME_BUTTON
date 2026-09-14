package com.savemebutton.phone.sos

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.SmsManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.savemebutton.shared.DispatcherConfig
import com.savemebutton.shared.SosConfig
import com.savemebutton.shared.SosCoords
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "SmbTel"
private const val SOS_DISPATCH_TAG = "SOS_DISPATCH"

class PhoneTelephony(private val context: Context) {

    @SuppressLint("MissingPermission")
    fun sendSms(number: String, body: String, coords: SosCoords?, config: SosConfig): Boolean {
        if (number.isBlank()) return false
        if (!hasPermission(Manifest.permission.SEND_SMS)) return false
        return runCatching {
            val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION") SmsManager.getDefault()
            }
            sms.sendTextMessage(number, null, body, null, null)

            dispatchToServer(source = "phone_sms_fallback", coords = coords, config = config, text = body)

            Log.d(TAG, "sms sent to $number")
            true
        }.getOrElse { Log.d(TAG, "sms failed: $it"); false }
    }

    fun placeCall(number: String, coords: SosCoords?, config: SosConfig): Boolean {
        if (number.isBlank()) return false
        if (!hasPermission(Manifest.permission.CALL_PHONE)) return false
        return runCatching {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(number)}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            dispatchToServer(
                source = "phone_button",
                coords = coords,
                config = config,
                text = "Apel de urgenta catre $number",
            )

            Log.d(TAG, "call dialed $number")
            true
        }.getOrElse { Log.d(TAG, "call failed: $it"); false }
    }

    /**
     * Fire-and-forget POST towards the demo AI dispatcher backend (backend_python/handler.py,
     * route /sos). Runs on its own thread so it never blocks the call/SMS flow which must
     * start first (confirmed correct order per review).
     */
    private fun dispatchToServer(source: String, coords: SosCoords?, config: SosConfig, text: String) {
        Thread {
            var conn: java.net.HttpURLConnection? = null
            try {
                val url = java.net.URL(DispatcherConfig.sosUrl())
                conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    connectTimeout = 2000
                    readTimeout = 2000
                }
                // TODO: SosConfig nu expune încă group_id/uid — folosim valori demo până
                // când configul le adaugă.
                val groupId = "demo-group"
                val uid = Build.MODEL
                val payload = JSONObject().apply {
                    put("source", source)
                    put("group_id", groupId)
                    put("uid", uid)
                    put("lat", coords?.lat ?: JSONObject.NULL)
                    put("lng", coords?.lon ?: JSONObject.NULL)
                    put("text", text)
                    put("ts", Instant.now().toString())
                }
                conn.outputStream.use { it.write(payload.toString().toByteArray()) }
                val code = conn.responseCode
                Log.i(SOS_DISPATCH_TAG, "dispatch ok, source=$source, responseCode=$code")
            } catch (e: Exception) {
                Log.e(SOS_DISPATCH_TAG, "dispatch failed, source=$source", e)
            } finally {
                conn?.disconnect()
            }
        }.start()
    }

    /**
     * After a call is answered (offhook), suspend until either the call goes idle
     * (peer hung up / user hung up) or `skipFlag` flips to true. Returns true if
     * skip was requested, false if the call ended on its own.
     */
    suspend fun awaitCallEndOrSkip(skipFlag: AtomicBoolean): Boolean {
        if (!hasPermission(Manifest.permission.READ_PHONE_STATE)) {
            while (!skipFlag.get()) delay(200)
            return true
        }
        val tm = context.getSystemService(TelephonyManager::class.java) ?: run {
            while (!skipFlag.get()) delay(200)
            return true
        }
        val ended = AtomicBoolean(false)
        val callback: Any
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    if (state == TelephonyManager.CALL_STATE_IDLE) ended.set(true)
                }
            }
            tm.registerTelephonyCallback(context.mainExecutor, cb)
            callback = cb
        } else {
            @Suppress("DEPRECATION")
            val cb = object : PhoneStateListener() {
                @Suppress("DEPRECATION")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    if (state == TelephonyManager.CALL_STATE_IDLE) ended.set(true)
                }
            }
            @Suppress("DEPRECATION") tm.listen(cb, PhoneStateListener.LISTEN_CALL_STATE)
            callback = cb
        }
        try {
            // Ignore the initial IDLE that fires right after registration; only
            // count IDLE that arrives after we've seen at least one tick.
            delay(400)
            ended.set(false)
            while (!skipFlag.get() && !ended.get()) delay(200)
        } finally {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                tm.unregisterTelephonyCallback(callback as TelephonyCallback)
            } else {
                @Suppress("DEPRECATION")
                tm.listen(callback as PhoneStateListener, PhoneStateListener.LISTEN_NONE)
            }
        }
        return skipFlag.get()
    }

    fun endCall(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        if (!hasPermission(Manifest.permission.ANSWER_PHONE_CALLS)) {
            Log.d(TAG, "endCall: no ANSWER_PHONE_CALLS permission")
            return false
        }
        val tc = context.getSystemService(TelecomManager::class.java) ?: return false
        return runCatching {
            @SuppressLint("MissingPermission")
            val ok = tc.endCall()
            Log.d(TAG, "endCall ok=$ok")
            ok
        }.getOrElse { Log.d(TAG, "endCall failed: $it"); false }
    }

    suspend fun waitForAnswer(timeoutMs: Long): Boolean {
        if (!hasPermission(Manifest.permission.READ_PHONE_STATE)) {
            delay(timeoutMs); return false
        }
        val tm = context.getSystemService(TelephonyManager::class.java) ?: run {
            delay(timeoutMs); return false
        }
        val answered = AtomicBoolean(false)
        val callback: Any
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    if (state == TelephonyManager.CALL_STATE_OFFHOOK) answered.set(true)
                }
            }
            tm.registerTelephonyCallback(context.mainExecutor, cb)
            callback = cb
        } else {
            @Suppress("DEPRECATION")
            val cb = object : PhoneStateListener() {
                @Suppress("DEPRECATION")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    if (state == TelephonyManager.CALL_STATE_OFFHOOK) answered.set(true)
                }
            }
            @Suppress("DEPRECATION") tm.listen(cb, PhoneStateListener.LISTEN_CALL_STATE)
            callback = cb
        }
        try {
            val deadline = System.currentTimeMillis() + timeoutMs
            withTimeoutOrNull(timeoutMs) {
                while (System.currentTimeMillis() < deadline) {
                    if (answered.get()) return@withTimeoutOrNull true
                    delay(200)
                }
                false
            }
        } finally {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                tm.unregisterTelephonyCallback(callback as TelephonyCallback)
            } else {
                @Suppress("DEPRECATION")
                tm.listen(callback as PhoneStateListener, PhoneStateListener.LISTEN_NONE)
            }
        }
        return answered.get()
    }

    private fun hasPermission(perm: String): Boolean =
        ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
}
