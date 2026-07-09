package com.savemebutton.wear.sos

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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "SmbTel"

class WatchTelephony(private val context: Context) {

    @SuppressLint("MissingPermission")
    fun sendSms(number: String, body: String): Boolean {
        if (number.isBlank()) return false
        if (!hasPermission(Manifest.permission.SEND_SMS)) {
            Log.d(TAG, "sendSms no permission")
            return false
        }
        return runCatching {
            val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            sms.sendTextMessage(number, null, body, null, null)
            Log.d(TAG, "sms sent to $number")
            true
        }.getOrElse {
            Log.d(TAG, "sms failed: $it")
            false
        }
    }

    fun placeCall(number: String): Boolean {
        if (number.isBlank()) return false
        if (!hasPermission(Manifest.permission.CALL_PHONE)) {
            Log.d(TAG, "placeCall no permission")
            return false
        }
        return runCatching {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(number)}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d(TAG, "call dialed $number")
            true
        }.getOrElse {
            Log.d(TAG, "call failed: $it")
            false
        }
    }

    /**
     * After a call is answered, suspend until the call goes idle or `skipFlag` flips.
     * Returns true if skip was requested.
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
            @Suppress("DEPRECATION")
            tm.listen(cb, PhoneStateListener.LISTEN_CALL_STATE)
            callback = cb
        }
        try {
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

    /** Returns true if offhook was observed within `timeoutMs`, false on timeout. */
    suspend fun waitForAnswer(timeoutMs: Long): Boolean {
        if (!hasPermission(Manifest.permission.READ_PHONE_STATE)) {
            Log.d(TAG, "waitForAnswer no READ_PHONE_STATE — fall back to timeout-only")
            delay(timeoutMs)
            return false
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
            @Suppress("DEPRECATION")
            tm.listen(cb, PhoneStateListener.LISTEN_CALL_STATE)
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
