package com.savemebutton.wear.sos

import android.content.Context
import android.content.pm.PackageManager
import android.telephony.ServiceState
import android.telephony.TelephonyManager
import android.util.Log
import com.savemebutton.shared.SosRoute

private const val TAG = "SmbCap"

class CapabilityProbe(private val context: Context) {
    fun resolveRoute(): SosRoute {
        val pm = context.packageManager
        if (!pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            Log.d(TAG, "no FEATURE_TELEPHONY -> PHONE")
            return SosRoute.PHONE
        }
        val tm = context.getSystemService(TelephonyManager::class.java)
        if (tm == null) {
            Log.d(TAG, "TelephonyManager null -> PHONE")
            return SosRoute.PHONE
        }
        if (tm.simState != TelephonyManager.SIM_STATE_READY) {
            Log.d(TAG, "simState=${tm.simState} -> PHONE")
            return SosRoute.PHONE
        }
        val ss: ServiceState? = try {
            tm.serviceState
        } catch (e: SecurityException) {
            Log.d(TAG, "serviceState SecurityException -> PHONE")
            return SosRoute.PHONE
        }
        if (ss == null || ss.state != ServiceState.STATE_IN_SERVICE) {
            Log.d(TAG, "serviceState=${ss?.state} -> PHONE")
            return SosRoute.PHONE
        }
        Log.d(TAG, "WATCH_SELF eligible")
        return SosRoute.WATCH_SELF
    }
}
