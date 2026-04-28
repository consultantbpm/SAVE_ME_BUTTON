package com.savemebutton.phone.sos

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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

class PhoneTelephony(private val context: Context) {

    @SuppressLint("MissingPermission")
    fun sendSms(number: String, body: String): Boolean {
        if (number.isBlank()) return false
        if (!hasPermission(Manifest.permission.SEND_SMS)) return false
        return runCatching {
            val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION") SmsManager.getDefault()
            }
            sms.sendTextMessage(number, null, body, null, null)
            Log.d(TAG, "sms sent to $number")
            true
        }.getOrElse { Log.d(TAG, "sms failed: $it"); false }
    }

    fun placeCall(number: String): Boolean {
        if (number.isBlank()) return false
        if (!hasPermission(Manifest.permission.CALL_PHONE)) return false
        return runCatching {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(number)}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d(TAG, "call dialed $number")
            true
        }.getOrElse { Log.d(TAG, "call failed: $it"); false }
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
