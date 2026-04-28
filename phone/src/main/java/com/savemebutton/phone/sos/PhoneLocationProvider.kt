package com.savemebutton.phone.sos

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.savemebutton.shared.SosCoords
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val FIX_BUDGET_MS = 2_000L

class PhoneLocationProvider(private val context: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun fetch(): SosCoords? {
        if (!hasPermission()) return null
        val last = lastKnown()
        if (last != null) return last
        return withTimeoutOrNull(FIX_BUDGET_MS) { currentFix() }
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
            .setDurationMillis(FIX_BUDGET_MS)
            .build()
        val task = client.getCurrentLocation(req, null)
        task.addOnSuccessListener { loc ->
            cont.resume(loc?.let { SosCoords(it.latitude, it.longitude) })
        }
        task.addOnFailureListener { cont.resume(null) }
    }
}
