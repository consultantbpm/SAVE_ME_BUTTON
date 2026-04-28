package com.savemebutton.wear.sos

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.savemebutton.shared.SosCoords
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val TAG = "SmbLoc"
private const val WATCH_FIX_BUDGET_MS = 2_000L
private const val PHONE_FALLBACK_BUDGET_MS = 2_000L

class LocationProvider(
    private val context: Context,
    private val remoteLocation: RemoteLocationBridge,
) {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun fetch(): SosCoords? {
        val local = if (hasPermission()) {
            val last = lastKnown()
            if (last != null) {
                Log.d(TAG, "lastKnown lat=${last.lat} lon=${last.lon}")
                last
            } else {
                val fresh = withTimeoutOrNull(WATCH_FIX_BUDGET_MS) { currentFix() }
                Log.d(TAG, "watch fresh=$fresh")
                fresh
            }
        } else {
            Log.d(TAG, "no FINE_LOCATION permission on watch")
            null
        }
        if (local != null) return local

        Log.d(TAG, "watch fix unavailable, asking phone")
        val phone = withTimeoutOrNull(PHONE_FALLBACK_BUDGET_MS) { remoteLocation.requestFromPhone() }
        Log.d(TAG, "phone fix=$phone")
        return phone
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private suspend fun lastKnown(): SosCoords? = suspendCancellableCoroutine { cont ->
        client.lastLocation
            .addOnSuccessListener { loc ->
                cont.resume(loc?.let { SosCoords(it.latitude, it.longitude) })
            }
            .addOnFailureListener { cont.resume(null) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentFix(): SosCoords? = suspendCancellableCoroutine { cont ->
        val req = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(0)
            .setDurationMillis(WATCH_FIX_BUDGET_MS)
            .build()
        val task = client.getCurrentLocation(req, null)
        task.addOnSuccessListener { loc ->
            cont.resume(loc?.let { SosCoords(it.latitude, it.longitude) })
        }
        task.addOnFailureListener { cont.resume(null) }
    }
}
